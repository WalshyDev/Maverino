package dev.walshy.blobrepo.obj;

import lombok.Data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Data
public class Repo {

    @Nonnull
    private final String id;
    @Nonnull
    private final String path;
    @Nullable
    private final String readAuth;
    @Nullable
    private final String writeAuth;
}
