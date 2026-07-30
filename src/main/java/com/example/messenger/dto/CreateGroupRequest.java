package com.example.messenger.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

public class CreateGroupRequest {

    @NotBlank(message = "Название группы обязательно")
    @Size(min = 1, max = 100, message = "Название группы должно быть не длиннее 100 символов")
    private String name;

    /** Only "GROUP" is creatable via the API; kept as a field for forward-compatibility. */
    @NotBlank
    private String type;

    /** Usernames to add as initial members. */
    private List<String> members;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }
}
