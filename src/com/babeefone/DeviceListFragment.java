package com.babeefone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.Set;

public class DeviceListFragment extends Fragment {

    private ArrayAdapter<String> pairedDevicesArrayAdapter;
    private ArrayAdapter<String> availableDevicesArrayAdapter;
    private BluetoothAdapter bluetoothAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.device_list, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        Button scanButton = (Button) getActivity().findViewById(R.id.scanButton);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
            }
        });

        Button homeButton = (Button) getActivity().findViewById(R.id.homeButton);
        homeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ((BootstrapActivity) getActivity()).goHome();
            }
        });

        availableDevicesArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.device_name);
        pairedDevicesArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.device_name);

        ListView pairedListView = (ListView) getActivity().findViewById(R.id.pairedDevicesList);
        pairedListView.setAdapter(pairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(deviceClickListener);

        ListView newDevicesListView = (ListView) getActivity().findViewById(R.id.availableDevicesList);
        newDevicesListView.setAdapter(availableDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(deviceClickListener);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        getActivity().registerReceiver(broadcastReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        getActivity().registerReceiver(broadcastReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            getActivity().findViewById(R.id.pairedDevicesTitle).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }

        doDiscovery();
    }

    @Override
    public void onStop() {
        super.onStop();

        getActivity().unregisterReceiver(broadcastReceiver);

        bluetoothAdapter.cancelDiscovery();
    }

    private void doDiscovery() {
        getActivity().findViewById(R.id.scanProgress).setVisibility(View.VISIBLE);
        getActivity().findViewById(R.id.scanButton).setEnabled(false);

        availableDevicesArrayAdapter.clear();

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        bluetoothAdapter.startDiscovery();
    }

    private AdapterView.OnItemClickListener deviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            bluetoothAdapter.cancelDiscovery();

            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            MainService mainService = ((BootstrapActivity) getActivity()).getMainService();
            if (mainService.getConnectedDevice() == null || !mainService.getConnectedDevice().equals(bluetoothDevice)) {
                mainService.disconnect();
                mainService.connect(bluetoothDevice);
            }
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    availableDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                getActivity().findViewById(R.id.scanProgress).setVisibility(View.INVISIBLE);
                getActivity().findViewById(R.id.scanButton).setEnabled(true);
            }
        }
    };
}