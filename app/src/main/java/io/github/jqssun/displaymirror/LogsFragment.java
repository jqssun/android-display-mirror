package io.github.jqssun.displaymirror;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.jqssun.displaymirror.job.FetchLogAndShare;
import io.github.jqssun.displaymirror.shizuku.ShizukuUtils;

public class LogsFragment extends Fragment {
    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_logs, container, false);

        logRecyclerView = view.findViewById(R.id.logRecyclerView);
        logAdapter = new LogAdapter(State.logs);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        logRecyclerView.setAdapter(logAdapter);
        _scrollToBottom();

        view.findViewById(R.id.btnExportLogs).setOnClickListener(v -> {
            if (!ShizukuUtils.hasPermission()) {
                Toast.makeText(getContext(), R.string.export_log_needs_shizuku, Toast.LENGTH_SHORT).show();
                return;
            }
            State.startNewJob(new FetchLogAndShare(getContext()));
        });

        view.findViewById(R.id.btnClearLogs).setOnClickListener(v -> {
            State.logs.clear();
            logAdapter.notifyDataSetChanged();
        });

        State.logVersion.observe(getViewLifecycleOwner(), version -> {
            logAdapter.notifyDataSetChanged();
            _scrollToBottom();
        });

        return view;
    }

    private void _scrollToBottom() {
        if (logAdapter != null && logAdapter.getItemCount() > 0) {
            logRecyclerView.scrollToPosition(logAdapter.getItemCount() - 1);
        }
    }
}
