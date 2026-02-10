package org.example.infra_fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.UUID;

public final class SafeFiles {
    private SafeFiles() {
    }

    public static void atomicWriteString(Path target, String content, Charset charset, boolean force) throws IOException {
        Files.createDirectories(target.getParent());

        // 不允許覆蓋時，且目標存在 → 直接拒絕
        if (!force && Files.exists(target)) {
            throw new FileAlreadyExistsException(target.toString());
        }

        // 同資料夾 tmp，確保 move 有機會 ATOMIC_MOVE
        String tmpName = target.getFileName() + ".tmp." + UUID.randomUUID();
        Path tmp = target.resolveSibling(tmpName);

        // 1) 先寫 tmp（一定要 close 才算完成）
        Files.writeString(tmp, content, charset, StandardOpenOption.CREATE_NEW);

        // 2) 原子搬移成正式檔
        try {
            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 檔案系統不支援 atomic move（例如跨磁碟或某些 FS）
            // 仍可退回非原子 move（至少 tmp→正式檔是完整檔，不會半寫）
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
