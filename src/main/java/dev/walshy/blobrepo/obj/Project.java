package dev.walshy.blobrepo.obj;

import lombok.Data;

@Data
public class Project {

    private final String groupId;
    private final String artifactId;
    private final String version;

    @Override
    public String toString() {
        return this.groupId + '.' + this.artifactId;
    }
}
