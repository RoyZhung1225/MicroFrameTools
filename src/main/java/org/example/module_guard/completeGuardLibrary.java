package org.example.module_guard;

import java.util.List;

public class completeGuardLibrary {

    public static final List<String> TARGET_FLAGS = List.of(
            "--file", "--all", "--dir", "--dir-name"
    );

    public static final List<String> ACTION_FLAGS = List.of(
            "--refresh-prefix", "--regen-uuid"
    );

    public static final List<String> MODE_FLAGS = List.of(
            "--apply", "--force"
    );

    public static final List<String> ALL_FLAGS = List.of(
            "--file", "--all", "--dir", "--dir-name",
            "--pick",
            "--refresh-prefix", "--regen-uuid",
            "--apply", "--force"
    );

}
