package io.github.jqssun.displaymirror;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
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
        logRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        logRecyclerView.setAdapter(logAdapter);
        logRecyclerView.setClipToPadding(false);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(logRecyclerView, (v, insets) -> {
            int bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            return insets;
        });
        _scrollToBottom();

        State.logVersion.observe(getViewLifecycleOwner(), version -> {
            logAdapter.notifyDataSetChanged();
            _scrollToBottom();
        });

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater2) {
                inflater2.inflate(R.menu.logs_menu, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_export) {
                    if (!ShizukuUtils.hasPermission()) {
                        Toast.makeText(requireContext(), R.string.export_log_needs_shizuku, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    State.startNewJob(new FetchLogAndShare(requireContext()));
                    return true;
                } else if (item.getItemId() == R.id.action_clear) {
                    State.logs.clear();
                    logAdapter.notifyDataSetChanged();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        return view;
    }

    private void _scrollToBottom() {
        if (logAdapter != null && logAdapter.getItemCount() > 0) {
            logRecyclerView.scrollToPosition(logAdapter.getItemCount() - 1);
        }
    }
}
