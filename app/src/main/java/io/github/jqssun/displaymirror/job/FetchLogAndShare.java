package io.github.jqssun.displaymirror.job;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;


import io.github.jqssun.displaymirror.R;
import io.github.jqssun.displaymirror.State;

import rikka.shizuku.Shizuku;

import java.io.File;
import java.io.IOException;

public class FetchLogAndShare implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private boolean userServiceRequested = false;

    private final Context context;

    public FetchLogAndShare(Context context) {
        this.context = context;
    }

    @Override
    public void start() throws YieldException {
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        if (State.userService == null) {
            if (!userServiceRequested) {
                userServiceRequested = true;
                Shizuku.peekUserService(State.userServiceArgs, State.userServiceConnection);
                Shizuku.bindUserService(State.userServiceArgs, State.userServiceConnection);
                State.resumeJobLater(1000);
                throw new YieldException("Waiting for user service to start");
            }
            Toast.makeText(State.getContext(), R.string.cannot_start_user_service, Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                State.getContext().startActivity(intent);
                Toast.makeText(State.getContext(), R.string.grant_file_permission, Toast.LENGTH_LONG).show();
                return;
            }
        }

        try {
            File downloadLogFile = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "Mirror.log");

            if (downloadLogFile.exists()) {
                downloadLogFile.delete();
            }

            State.userService.fetchLogs();

            if (!downloadLogFile.exists()) {
                Toast.makeText(State.getContext(), R.string.log_file_not_generated, Toast.LENGTH_SHORT).show();
                return;
            }

            File cacheDir = State.getContext().getCacheDir();
            File cacheCopyFile = new File(cacheDir, "Mirror.log");

            java.nio.file.Files.copy(
                    downloadLogFile.toPath(),
                    cacheCopyFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            Uri fileUri = FileProvider.getUriForFile(State.getContext(),
                    State.getContext().getPackageName() + ".provider",
                    cacheCopyFile);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            State.getContext().startActivity(Intent.createChooser(shareIntent, State.getContext().getString(R.string.share_log_file)));

        } catch (RemoteException | IOException e) {
            Toast.makeText(State.getContext(), R.string.check_log_file_export, Toast.LENGTH_LONG).show();
            throw new RuntimeException(e);
        }
    }
}
