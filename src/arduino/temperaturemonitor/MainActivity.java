package arduino.temperaturemonitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

@SuppressLint("SimpleDateFormat")
public class MainActivity extends SherlockActivity {

	protected static final String SWITCH_ON = "1";
	protected static final String SWITCH_OFF = "0";
	private static final int ERROR_ALERT = 0;
	private static final int WARNING_ALERT = 1;
	private static final int INFO_ALERT = 2;
	private TextView alerttextView, temTextView, 
					 typeTextView, reading_textView,
					 timeTextView, dateTextView;
	private Button turn_heat_on_button, turn_heat_off_button;
	private RadioButton toFehButton, toCelButton;

	private boolean toFahrenheit = false;
	
	// other instances	
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothDevice mBluetoothDevice;
	private BluetoothSocket mBluetoothSocket;
	
	private OutputStream mOutputStream;
	private InputStream mInputStream;
	
	
	// and others
	private volatile boolean stopWorker;
	private int readBufferPosition;
	private byte[] readBuffer;
	private Thread workerThread;
	private Thread clockThread;
	
	private static String Celcius = "celcius";
	private static String Fahreinheit = "fahreinheit";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		initializeObjects();  // initializing objects
		
		setButtonClickListeners(); // setting event click listeners
		
		initializeViewClock();	// stting the ticker clock in view
		
		if (BtDeviceFound()) { // check to see if bluetooth device exists and it's enabled
		    //beginListenForData();			
		} else {
			Toast.makeText(MainActivity.this, "Error occured while accessing bluetooth.", Toast.LENGTH_LONG).show();
		}
	}
	
	private void initializeViewClock(){

		final Clock c = new Clock(MainActivity.this, 0);
		final Handler handler = new Handler();
		
		clockThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				final SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM, dd yyyy");
				final String currentDate = sdf.format(new Date());				
				
				c.AddClockTickListner(new OnClockTickListner() {
					String time ="";
					
					@Override
					public void OnSecondTick(Time currentTime) {
						time = DateFormat.format("h:mm:ss aa ", currentTime.toMillis(true)).toString();
						handler.post(new Runnable() {

							public void run() {
								dateTextView.setText(currentDate);
								timeTextView.setText(time);		
								
							}
						});
					}

					@Override
					public void OnMinuteTick(Time currentTime) {

						time = DateFormat.format("h:mm aa", currentTime.toMillis(true)).toString();	
						handler.post(new Runnable() {

							public void run() {
								dateTextView.setText(currentDate);
								timeTextView.setText(time);		
								
							}
						});
					}
							            
		        });
				
				
			}
		});
		
		clockThread.run();
		
	}
	
	/**
	 * Initializing objects
	 */
	private void initializeObjects(){
		// Creating instances of TextView objects
		alerttextView = (TextView) findViewById(R.id.alerttextView);
		temTextView = (TextView) findViewById(R.id.temptextView);
		typeTextView = (TextView) findViewById(R.id.typetextView);
		reading_textView = (TextView) findViewById(R.id.reading_textView);
		timeTextView = (TextView) findViewById(R.id.time_textView);
		dateTextView = (TextView) findViewById(R.id.date_textView);
		
		// radiobuttons
		toFehButton = (RadioButton) findViewById(R.id.to_feh_radio);
		toCelButton = (RadioButton) findViewById(R.id.to_deg_radio);
		
		// Creating instances of Button objects
		turn_heat_on_button = (Button) findViewById(R.id.turn_heat_on_button);
		turn_heat_off_button = (Button) findViewById(R.id.turn_heat_off_button);
	}
	
	private void setButtonClickListeners(){
		
		toFehButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked){
					int value = Integer.parseInt(temTextView.getText().toString());
					typeTextView.setText(getResources().getString(R.string.to_fahreinheit));
					toFahrenheit = true;
					temTextView.setText(convertValueTo(Fahreinheit, value));
				}
				
			}
		});
		
		toCelButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked){
					int value = Integer.parseInt(temTextView.getText().toString());
					typeTextView.setText(getResources().getString(R.string.to_degrees));
					toFahrenheit = false;
					temTextView.setText(convertValueTo(Celcius, value));
				}
				
			}
		});
		turn_heat_on_button.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				/**
				 * Turn Heat source on, on arduino
				 */
				// send a serial message of value '1' to arduino
				sendData(SWITCH_ON);
			}
		});
		
		turn_heat_off_button.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				/**
				 * Turn Heat source off, on arduino
				 */
				// send a serial message of value '2' to arduino
				sendData(SWITCH_OFF);
			}
		});
		
	}
	
	
	private boolean BtDeviceFound(){
		
		if (checkBTdevice()){
			openBT();
		}
		else {
			return false;
		}
		return true;
	}
	

	private void alertMessage(int TYPE, String msg){
		LinearLayout alertLayout = (LinearLayout) findViewById(R.id.alertLayout);
		switch (TYPE) {
		case ERROR_ALERT:
			alertLayout.setBackgroundColor(getResources().getColor(R.color.red));
			
			break;
		case WARNING_ALERT:
			alertLayout.setBackgroundColor(getResources().getColor(R.color.blue));
			
			break;
		case INFO_ALERT:
			alertLayout.setBackgroundColor(getResources().getColor(R.color.green));
			break;
		default:
			break;
		}
		alerttextView.setText(msg);
	}
	/**
	 * Searches for bluetooth device
	 * Checks if bluetooth device is enabled
	 * Connect to firefly device {FireFly-108B}
	 */
	private boolean checkBTdevice() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	    if(mBluetoothAdapter == null) {  // check to see if device has a bluetooth adapter
	    	// alerttextView.setTextColor();
	    	alertMessage(ERROR_ALERT, "No bluetooth adapter available");
	    	return false;
	    }

	    if(!mBluetoothAdapter.isEnabled()) { // Check to see if adapter is enabled
	    	alertMessage(ERROR_ALERT, "Bluetooth adapter not on! \nVisit menu to enable bluetooth.");	    	
	    	return false;
	    }
	    
	    alertMessage(WARNING_ALERT, "Bluetooth device is on");
	    return this.createPairing();
	}
	
	/**
	 * Create pairing with the 
	 * arduino board
	 */
	private boolean createPairing(){
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
		if(pairedDevices.size() > 0) {
			for(BluetoothDevice device : pairedDevices) {
				if(device.getName().equals("FireFly-108B")) { // Finds the device to be connected to
					mBluetoothDevice = device;
					break;
				}
			}
		}
		
		if (mBluetoothDevice==null){ // If bluetooth device was connected to not
			alertMessage(WARNING_ALERT, "Firefly-108B is not set for connectivity.\nPlease reset device");
			return false;
		} else {
			alertMessage(INFO_ALERT, "Connection Established with FireFly-108A!. Listening...");
		}
		
		return true;
	}
	
	/**
	 * Open the bluetooth device socket/serial for communication
	 */
	private void openBT() {
		UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard //SerialPortService ID
		try {
			mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(uuid);
		    mBluetoothSocket.connect();
		    mOutputStream = mBluetoothSocket.getOutputStream();
		    mInputStream = mBluetoothSocket.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}    
	    reading_textView.setVisibility(View.VISIBLE);
	}
	
	/**
	 * Listening for data from connected bluetooth device
	 */
	@SuppressWarnings("unused")
	private void beginListenForData(){
		final Handler handler = new Handler();
		final byte delimiter = 10; // ASCII code for a newline character

	    stopWorker = false;
	    readBufferPosition = 0;
	    readBuffer = new byte[1024];
	    workerThread = new Thread(new Runnable() {
	    	public void run() {
	    		while(!Thread.currentThread().isInterrupted() && !stopWorker) {
	    			try {
	    				int bytesAvailable = mInputStream.available();
	    				
	    				if(bytesAvailable > 0) { // If there is a message from the device
	    					byte[] packetBytes = new byte[bytesAvailable];
	    					mInputStream.read(packetBytes);
	    					for(int i=0;i<bytesAvailable;i++) {
	    						byte b = packetBytes[i];
	    						if(b == delimiter) { // if byte is equal to a newline
	    							byte[] encodedBytes = new byte[readBufferPosition];
	    							System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
	    							final String data = new String(encodedBytes, "US-ASCII");
	    							readBufferPosition = 0;

	    							handler.post(new Runnable() {

										public void run() {
	    									// Check the display nature of the reading(Celcius or Fahrenheit)
											String returnValue = data;
	    									if (toFahrenheit ){
	    										returnValue = convertValueTo(Fahreinheit, Integer.parseInt(data));
	    									}	    									
	    									temTextView.setText(returnValue);
	    									
	    								}
	    							});
	    						}
	    						else {
	    							readBuffer[readBufferPosition++] = b;
	    						}
	    					}
	    				}
	    			}
	    			catch (IOException ex) {
	    				stopWorker = true;
	    			}
	    		}
	    	}
	    });

	    workerThread.start();
	}
	
	/**
	 * Convert value into another unit of conversion
	 * @param object
	 * @param value
	 * @return
	 */
	private String convertValueTo(String object, int value){
		Integer result = 21;
		if (object == Celcius) { // Convert to celcius
			result  = ((value-32)*5)/9;
		} else if (object == Fahreinheit) { // convert to fahreinheit
			result = ((value * 9)/5)+32;
		}
		
		return result.toString();
	}
	
	/**
	 * Send message to arduino device
	 * @param switch_msg
	 * @throws IOException
	 */
	private void sendData(String switch_msg) {
		try {
			mOutputStream.write(switch_msg.getBytes());
		} catch (IOException e) {
			Toast.makeText(MainActivity.this, "An error occured while switching device!", Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
		// myLabel.setText("Data Sent");
	  }
	
	/**
	 * Close bluetooth connection and any other
	 * @throws IOException
	 */
	@SuppressWarnings("unused")
	private void closeBT() {
		stopWorker = true;
		try {
			mOutputStream.close();
			mInputStream.close();
			mBluetoothSocket.close();
		} catch (IOException e) {
			Toast.makeText(MainActivity.this, "An error occured while closing streams and sockets", Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
		alertMessage(WARNING_ALERT, "Bluetooth Closed");
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getSupportMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			android.util.Log.i("Hey", "Settings clicked");
			break;
		case R.id.action_turn_on_bluetooth:
			this.TurnOnBluetooth();
			android.util.Log.i("Hey", "Turn on bluetooth");
		default:
			break;
		}
		item.getItemId();
		return super.onOptionsItemSelected(item);
	}
	
	/**
	 * Turning on the Bluetooth adapter
	 */
	private void TurnOnBluetooth(){
		if(mBluetoothAdapter !=null){
			if(!mBluetoothAdapter.isEnabled()) { // Check to see if adapter is enabled
				Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBluetooth, 1);
			} else {
				Toast.makeText(MainActivity.this, "Bluetooth already on!", Toast.LENGTH_SHORT).show();
			}
		} else{
			alertMessage(ERROR_ALERT, "Device doesn't have a bluetooth adapter!");
		}
		
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case 1:
			if (resultCode == RESULT_OK){
				this.createPairing();
				Toast.makeText(MainActivity.this, "Bluetooth switched on!", Toast.LENGTH_SHORT).show();
			}
			if (resultCode == RESULT_CANCELED){
				Toast.makeText(MainActivity.this, "Something went wrong turning bluetooth on", Toast.LENGTH_SHORT).show();
			}
			break;

		default:
			break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	@Override
	protected void onDestroy() {
		// closeBT();
		super.onDestroy();
	}

}
