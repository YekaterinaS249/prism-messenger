package com.example.messenger.service;

import com.example.messenger.dto.LinkPreviewDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches minimal Open Graph / <title> metadata for a URL so the chat can show a link preview
 * card. Nothing is cached or persisted server-side; every request re-fetches. Guards against
 * SSRF: only http(s), only the final (redirect-followed) host is allowed, private/loopback/
 * link-local addresses are rejected, and the response body is capped and must be text/html.
 */
@Service
public class LinkPreviewService {

    private static final int MAX_BODY_BYTES = 300_000;
    private static final int MAX_REDIRECTS = 3;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER) // we follow manually so each hop can be validated
            .build();

    public LinkPreviewDto fetch(String rawUrl) {
        URI uri = parseAndValidate(rawUrl);
        if (uri == null) return null;

        HttpResponse<InputStream> response = null;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            response = requestOnce(uri);
            if (response == null) return null;
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) return null;
                URI next = safeResolve(uri, location);
                if (next == null) return null;
                uri = next;
                continue;
            }
            break;
        }
        if (response == null || response.statusCode() != 200) return null;

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.toLowerCase().contains("text/html")) return null;

        String html = readCapped(response.body());
        if (html == null) return null;

        return parse(uri.toString(), html);
    }

    private HttpResponse<InputStream> requestOnce(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (compatible; PrismLinkPreview/1.0)")
                    .GET()
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            return null;
        }
    }

    private String readCapped(InputStream in) {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buf)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) break;
                out.write(buf, 0, read);
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private URI parseAndValidate(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            return isAllowed(uri) ? uri : null;
        } catch (Exception e) {
            return null;
        }
    }

    private URI safeResolve(URI base, String location) {
        try {
            URI resolved = base.resolve(location);
            return isAllowed(resolved) ? resolved : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAllowed(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return false;
        String host = uri.getHost();
        if (host == null || host.isBlank()) return false;
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private LinkPreviewDto parse(String url, String html) {
        String ogTitle = metaContent(html, "og:title");
        String title = ogTitle != null ? ogTitle : tag(html, "title");
        String description = metaContent(html, "og:description");
        if (description == null) description = metaName(html, "description");
        String image = metaContent(html, "og:image");
        String siteName = metaContent(html, "og:site_name");

        if (image != null) {
            try {
                image = URI.create(url).resolve(image).toString();
            } catch (Exception ignored) {
                // leave as-is if it can't be resolved against the page URL
            }
        }

        if (title == null && description == null && image == null) return null;
        return new LinkPreviewDto(url, trim(title), trim(description), image, trim(siteName));
    }

    private String trim(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    private String tag(String html, String tagName) {
        Matcher m = Pattern.compile("<" + tagName + "[^>]*>([^<]*)</" + tagName + ">", Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? unescapeHtml(m.group(1)) : null;
    }

    private String metaContent(String html, String property) {
        Matcher m = Pattern.compile(
                "<meta[^>]+property=[\"']" + Pattern.quote(property) + "[\"'][^>]+content=[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return unescapeHtml(m.group(1));
        // some sites put content before property
        m = Pattern.compile(
                "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']" + Pattern.quote(property) + "[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? unescapeHtml(m.group(1)) : null;
    }

    private String metaName(String html, String name) {
        Matcher m = Pattern.compile(
                "<meta[^>]+name=[\"']" + Pattern.quote(name) + "[\"'][^>]+content=[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return unescapeHtml(m.group(1));
        m = Pattern.compile(
                "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+name=[\"']" + Pattern.quote(name) + "[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? unescapeHtml(m.group(1)) : null;
    }

    private String unescapeHtml(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">");
    }
}
