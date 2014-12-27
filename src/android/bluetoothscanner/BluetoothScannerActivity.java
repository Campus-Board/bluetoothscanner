package android.bluetoothscanner;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxDatastore;
import com.dropbox.sync.android.DbxDatastoreManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxFields;
import com.dropbox.sync.android.DbxRecord;
import com.dropbox.sync.android.DbxTable;

@SuppressLint("UseSparseArrays")
public class BluetoothScannerActivity extends Activity {
	private static final String APP_KEY = "ek6r03dt5wsbxsl";
	private static final String APP_SECRET = "72b983r0cwk45yo";
	private static final int REQUEST_LINK_TO_DBX = 0;
	private static final int SCAN_TIMES = 1;
	
	private Button connectButton;
	private Button sendButton;
	private Button scanButton;
	private ToggleButton togglePower;
	private ToggleButton toggleMode;
	
	private DbxAccountManager mAccountManager;
	private DbxDatastoreManager mDatastoreManager;

	private BluetoothAdapter myBluetoothAdapter;
	private ArrayAdapter<String> btArrayAdapter;

	private ListView deviceslist;
	private Map<String, BluetoothDevice> devices = new HashMap<String, BluetoothDevice>();
	private Map<Integer, String> distances = new HashMap<Integer, String>();

	private boolean power = false;
	private boolean test_mode = false;
	private String deviceAddress = "";
	private int counterTime = 0;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		scanButton = (Button) findViewById(R.id.button);
		scanButton.setEnabled(false);
		deviceslist = (ListView) findViewById(R.id.listView1);

		btArrayAdapter = new ArrayAdapter<String>(BluetoothScannerActivity.this, android.R.layout.simple_list_item_1);
		deviceslist.setAdapter(btArrayAdapter);

		if (myBluetoothAdapter == null) {
			Toast.makeText(BluetoothScannerActivity.this, "Your device doesnot support Bluetooth", Toast.LENGTH_LONG)
					.show();
		} else if (!myBluetoothAdapter.isEnabled()) {
			Intent BtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(BtIntent, 0);
			Toast.makeText(BluetoothScannerActivity.this, "Turning on Bluetooth", Toast.LENGTH_LONG).show();
		}

		scanButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				bluetoothScan();
			}
		});

		registerReceiver(FoundReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
		registerReceiver(FoundReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

		mAccountManager = DbxAccountManager.getInstance(getApplicationContext(), APP_KEY, APP_SECRET);

		connectButton = (Button) findViewById(R.id.link_button);
		connectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				connectToDropbox();
				togglePower.setEnabled(true);
			}
		});

		sendButton = (Button) findViewById(R.id.send_button);
		sendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				updateDropboxSimulator();
			}
		});
		sendButton.setVisibility(View.GONE);
		sendButton.setEnabled(false);
		
		togglePower = (ToggleButton) findViewById(R.id.power_button);
		togglePower.setEnabled(false);
		togglePower.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		        if (isChecked) {
		            power = true;
		            bluetoothScan();
		            toggleMode.setEnabled(true);
		        } else {
		            power = false;
		            toggleMode.setEnabled(false);
		        }
		    }
		});
		
		toggleMode = (ToggleButton) findViewById(R.id.mode_button);
		toggleMode.setEnabled(false);
		toggleMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		        if (isChecked) {
		            test_mode = true;
		            scanButton.setEnabled(true);
		            sendButton.setEnabled(true);
		        } else {
		            test_mode = false;
		            scanButton.setEnabled(false);
		            sendButton.setEnabled(false);
		        }
		    }
		});
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(FoundReceiver);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_LINK_TO_DBX) {
			if (resultCode == Activity.RESULT_OK) {
				task();
			} else {
				Toast.makeText(BluetoothScannerActivity.this, "Account is not connected", Toast.LENGTH_LONG).show();
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	private void connectToDropbox() {
		mAccountManager.startLink((Activity) this, REQUEST_LINK_TO_DBX);
	}

	private final BroadcastReceiver FoundReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
				
				devices.put(device.getAddress(), device);
				distances.put(Integer.valueOf(rssi), device.getAddress());
				
				btArrayAdapter.add(device.getName() + "\n" + device.getAddress());
				btArrayAdapter.notifyDataSetChanged();
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				counterTime++;
				if (counterTime >= SCAN_TIMES) {
					counterTime = 0;
					int max = Integer.MIN_VALUE;
					for (Integer rssi : distances.keySet()) {
						if (rssi >= max) {
							max = rssi;
						}
					}
					if (max != Integer.MIN_VALUE) {
						BluetoothDevice device = devices.get(distances.get(Integer.valueOf(max)));
						Toast.makeText(BluetoothScannerActivity.this, device.getName()+"-"+device.getAddress()+"-"+max, Toast.LENGTH_LONG).show();
						if (!deviceAddress.equals(device.getAddress())) {
							deviceAddress = device.getAddress();
							updateDropbox(device);
						}
					} else {
						if (!deviceAddress.equals("")) {
							deviceAddress = "";
							updateDropbox(null);
						}
					}
					devices = new HashMap<String, BluetoothDevice>();
					distances = new HashMap<Integer, String>();
				}
				bluetoothScan();
			}
		}
	};
	
	private void bluetoothScan() {
		if (!power) return;
		btArrayAdapter.clear();
		myBluetoothAdapter.startDiscovery();
		Toast.makeText(BluetoothScannerActivity.this, "Scanning Devices", Toast.LENGTH_LONG).show();
	}

	private void task() {
		if (mAccountManager.hasLinkedAccount()) {
			connectButton.setVisibility(View.GONE);
			sendButton.setVisibility(View.VISIBLE);
			try {
				mDatastoreManager = DbxDatastoreManager.forAccount(mAccountManager.getLinkedAccount());
			} catch (DbxException.Unauthorized e) {
				Toast.makeText(BluetoothScannerActivity.this, "Account was disconnected remotely", Toast.LENGTH_LONG).show();
			}
		} else {
			connectButton.setVisibility(View.VISIBLE);
			sendButton.setVisibility(View.GONE);
			Toast.makeText(BluetoothScannerActivity.this, "Account was not connected", Toast.LENGTH_LONG).show();
		}
	}

	private void updateDropbox(BluetoothDevice device) {
		if (!power) return;
		if (test_mode) return;
		
		String address = "";
		String name = "";
		if (device != null) {
			address = device.getAddress();
			name = device.getName();
		}
		try {
			DbxDatastore datastore = mDatastoreManager.openDefaultDatastore();
			DbxTable bluetoothTbl = datastore.getTable("bluetooth");

			DbxFields queryParams = new DbxFields().set("id", "aJSd8aNs2");
			DbxTable.QueryResult results = bluetoothTbl.query(queryParams);
			
			DbxRecord firstBluetooth = null;
			if (results.asList().size() <= 0) {
				firstBluetooth = bluetoothTbl.insert()
						.set("id", "aJSd8aNs2")
						.set("mac", address)
						.set("name", name)
						.set("created", new Date());
				datastore.sync();
			} else {
				firstBluetooth = results.iterator().next();
				firstBluetooth.set("mac", address);
				firstBluetooth.set("name", name);
				firstBluetooth.set("created", new Date());
				datastore.sync();
			}
			Toast.makeText(BluetoothScannerActivity.this, "mac: "+firstBluetooth.getString("mac"), Toast.LENGTH_LONG).show();
			datastore.close();
		} catch (DbxException e) {
			Toast.makeText(BluetoothScannerActivity.this, "DbxException: salio un error!", Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
	}

	private void updateDropboxSimulator() {
		if (!power) return;
		if (!test_mode) return;
		try {
			DbxDatastore datastore = mDatastoreManager.openDefaultDatastore();
			DbxTable bluetoothTbl = datastore.getTable("bluetooth");
			Log.i("jael.table", bluetoothTbl.toString());

			DbxFields queryParams = new DbxFields().set("id", "aJSd8aNs2");
			DbxTable.QueryResult results = bluetoothTbl.query(queryParams);
			Log.i("jael.result", results.toString());
			DbxRecord firstBluetooth = null;
			if (results.asList().size() <= 0) {
				firstBluetooth = bluetoothTbl.insert()
						.set("id", "aJSd8aNs2")
						.set("mac", "A1:R0:12:09:1J")
						.set("name", "Jael")
						.set("created", new Date());
				datastore.sync();
				Log.i("jael.bluetooth", "created");
			} else {
				// firstBluetooth = results.asList().get(0);
				firstBluetooth = results.iterator().next();
				Log.i("jael.bluetooth", "retrieved");
			}
			Log.i("jael.bluetooth.mac", firstBluetooth.getString("mac"));

			if (firstBluetooth.getString("mac").equals("")) {
				firstBluetooth.set("mac", "A1:R0:12:09:1J");
			} else {
				firstBluetooth.set("mac", "");
			}
			datastore.sync();
			Log.i("jael.bluetooth.mac", firstBluetooth.getString("mac"));
			
			datastore.close();
		} catch (DbxException e) {
			Log.e("jael.error", "sucedio un error");
			e.printStackTrace();
		}
	}
}