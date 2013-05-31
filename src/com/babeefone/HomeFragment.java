package com.babeefone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class HomeFragment extends Fragment {

    private Button modeButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        modeButton = (Button) getActivity().findViewById(R.id.parent);
        modeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MainService mainService = ((BootstrapActivity) getActivity()).getMainService();
                mainService.setMode(mainService.getMode() == MainService.MODE_PARENT ? MainService.MODE_BABY : MainService.MODE_PARENT);
                mainService.setConnectedDeviceMode();
            }
        });

        updateMode();

        Button exitButton = (Button) getActivity().findViewById(R.id.exit);
        exitButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ((BootstrapActivity) getActivity()).exit();
            }
        });

        Button connectButton = (Button) getActivity().findViewById(R.id.connectButton);
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ((BootstrapActivity) getActivity()).goDevices();
            }
        });

        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(MainService.BROADCAST_ACTION));
    }

    @Override
    public void onStop() {
        super.onStop();

        getActivity().unregisterReceiver(broadcastReceiver);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int type = intent.getIntExtra("type", 0);
            switch (type) {
                case BootstrapActivity.MESSAGE_MODE_CHANGE:
                    updateMode();
                    break;
            }

        }
    };

    private void updateMode() {
        if (modeButton != null) {
            if (((BootstrapActivity) getActivity()).getMainService().getMode() == MainService.MODE_BABY) {
                modeButton.setText(R.string.parent);
            } else {
                modeButton.setText(R.string.baby);
            }
        }
    }
}