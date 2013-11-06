package com.flyq.obd2monitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.app.Activity;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class OBD2MonitorMainActivity extends Activity {

    // Message types sent from the OBD2MonitorService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // LogFile
    public static final String DIR_NAME_OBD2_MONITOR = "OBDIIMonitorLog";
    public static final String FILE_NAME_OBD2_MONITOR_LOG = "obd2_monitor_log.txt";

    // Key names received from the OBD2MonitorService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    public enum AUTO_RES {
        AUTO_RES_NONE,
        AUTO_RES_OK,
        AUTO_RES_NORMAL,
        AUTO_RES_ERROR
    };

    private AUTO_RES autoRes = null;

    private String mConnectedDeviceName = null;
    // Bluetooth
    BluetoothAdapter mBluetoothAdapter = null;
    OBD2MonitorService mOBD2MonitorService = null;

    // widgets defination
    private TextView mConnectedStatusTxt = null;
    private TextView mResponseMessageTxt = null;
    private TextView mSupportedPidsTxt = null;
    private EditText mInputOBD2CMDEditTxt = null;
    private Button mSendOBD2CMDBtn = null;
    private Button mSelectBtDevicesBtn = null;
    private Button mDisconnectDeviceBtn = null;
    private Spinner mCommandsSpinner = null;

    private static StringBuilder mCmdAndRes = null;

    // menu items
    private MenuItem mItemSetting = null;
    private MenuItem mItemQuit = null;
    private MenuItem mItemHelp = null;
    private MenuItem mItemAutoResOK = null;
    private MenuItem mItemAutoResNormal = null;
    private MenuItem mItemAutoResError = null;
    private static final String[] PIDS = {
            "01","02","03","04","05","06","07","08",
            "09","0A","0B","0C","0D","0E","0F","10",
            "11","12","13","14","15","16","17","18",
            "19","1A","1B","1C","1D","1E","1F","20"};

    private final Handler mMsgHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            switch (msg.what){
                case MESSAGE_STATE_CHANGE:
                    switch(msg.arg1){
                        case OBD2MonitorService.STATE_CONNECTING:
                            setConnectedStatusTitle(R.string.device_connecting);
                            break;
                        case OBD2MonitorService.STATE_CONNECTED:
                            mSendOBD2CMDBtn.setEnabled(true);
                            mDisconnectDeviceBtn.setEnabled(true);
                            setConnectedStatusTitle(mConnectedDeviceName);
                            break;
                        case OBD2MonitorService.STATE_LISTEN:
                        case OBD2MonitorService.STATE_NONE:
                            mSendOBD2CMDBtn.setEnabled(false);
                            mDisconnectDeviceBtn.setEnabled(false);
                            mConnectedStatusTxt.setText("");
                            break;
                        default:
                            break;
                    }
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    if(autoRes == AUTO_RES.AUTO_RES_NONE){
                        setPidsSupported(readMessage);
                        mCmdAndRes.append(" > Receive: " + readMessage);
                        mCmdAndRes.append('\n');
                        mResponseMessageTxt.setText(mCmdAndRes.toString());
    //                    writeOBD2MonitorLog("Receive: "+ readMessage);
                    }else{
                        autoResponse(readMessage);
                    }
                    break;
                case MESSAGE_WRITE:
//                byte[] writeBuf = (byte[]) msg.obj;
//                // construct a string from the buffer
//                String writeMessage = new String(writeBuf);
//                sendCMDMessage(writeMessage);
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    break;
                default:
                    break;
            }
        }

    };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_obd2_monitor_main);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            Toast.makeText(this, R.string.bt_not_available,
                    Toast.LENGTH_LONG).show();
            finish();
        }

        mConnectedStatusTxt  = (TextView)findViewById(R.id.connected_status_text);
        mResponseMessageTxt  = (TextView)findViewById(R.id.response_msg_text);
        mResponseMessageTxt.setMovementMethod(ScrollingMovementMethod.getInstance());
        mSupportedPidsTxt = (TextView)findViewById(R.id.supported_pids_text);
        mSupportedPidsTxt.setMovementMethod(ScrollingMovementMethod.getInstance());
        mInputOBD2CMDEditTxt = (EditText)findViewById(R.id.input_cmd_edit);

        mSelectBtDevicesBtn  = (Button)findViewById(R.id.select_device_btn);
        mSelectBtDevicesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonOnClick(view);
            }
        });

        mCommandsSpinner = (Spinner)findViewById(R.id.spinner);

        String[] mItems = getResources().getStringArray(R.array.ATandOBD2Commands);
        // 建立Adapter并且绑定数据源
        ArrayAdapter<String> _Adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mItems);
        //绑定 Adapter到控件
        mCommandsSpinner.setAdapter(_Adapter);
        mCommandsSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if(i > 0){
                    String itemStr = adapterView.getItemAtPosition(i).toString();
                    String[] tmpStr = itemStr.split("->");
                    String cmd = tmpStr[0];
                    mInputOBD2CMDEditTxt.setText(cmd);
                }else{
                    mInputOBD2CMDEditTxt.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                Toast.makeText(OBD2MonitorMainActivity.this, "Nothing to selected", Toast.LENGTH_LONG).show();
            }
        });

        mCmdAndRes = new StringBuilder();
        autoRes = AUTO_RES.AUTO_RES_NONE;
	}

    private void buttonOnClick(View v){
        switch (v.getId()){
            case R.id.send_cmd_btn:
                sendOBD2CMD();
                break;
            case R.id.select_device_btn:
                selectDevice();
                break;
            case R.id.disconnect_device_btn:
                disconncetDevice();
                break;
            default:
                break;
        }
    }

    private void setupOBDMonitor(){
        mSendOBD2CMDBtn       = (Button)findViewById(R.id.send_cmd_btn);
        mSendOBD2CMDBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonOnClick(view);
            }
        });

        mDisconnectDeviceBtn = (Button)findViewById(R.id.disconnect_device_btn);
        mDisconnectDeviceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonOnClick(view);
            }
        });

        mOBD2MonitorService = new OBD2MonitorService(this, mMsgHandler);
    }

    private void setConnectedStatusTitle(CharSequence title){
        mConnectedStatusTxt.setText(title);
    }

    private void setConnectedStatusTitle(int resID){
        mConnectedStatusTxt.setText(resID);
    }

    private void autoResponse(String resMsg){
        try{
            Thread.sleep(1000);
        }catch (InterruptedException e){
            e.printStackTrace();
        }
        String res = resMsg.replace("\r", "");
        res = res.trim();
        if(res.equals("atz"))
            sendOBD2CMD("ELM327 1.5 >");
        if(res.equals("atws"))
            sendOBD2CMD("ELM327 1.5 warm start >");
        if(res.equals("ate0"))
            sendOBD2CMD("ok >");
        if(res.equals("atl0"))
            sendOBD2CMD("ok >");
        if(res.equals("atsp0"))
            sendOBD2CMD("ok >");
        if(res.equals("0100")){
            sendOBD2CMD("SEARCHING..." + "41 00 BE 1F A8 13 >");
        }
        if(res.equals("0105")){
            if(autoRes == AUTO_RES.AUTO_RES_OK || autoRes == AUTO_RES.AUTO_RES_NORMAL)
                sendOBD2CMD("41 05 7B  >");
            if(autoRes == AUTO_RES.AUTO_RES_ERROR)
                sendOBD2CMD("CAN ERROR >");
        }
        if(res.equals("010B")){
            if(autoRes == AUTO_RES.AUTO_RES_OK || autoRes == AUTO_RES.AUTO_RES_NORMAL)
                sendOBD2CMD("41 0B 1A >");
            if(autoRes == AUTO_RES.AUTO_RES_ERROR)
                sendOBD2CMD("CAN ERROR >");
        }
        if(res.equals("010C")){
            if(autoRes == AUTO_RES.AUTO_RES_OK )
                sendOBD2CMD("41 0C 1A F8 >");
            if(autoRes == AUTO_RES.AUTO_RES_NORMAL)
                sendOBD2CMD("NO DATA >");
            if(autoRes == AUTO_RES.AUTO_RES_ERROR)
                sendOBD2CMD("CAN ERROR >");
        }
        if(res.equals("0101")){
            if(autoRes == AUTO_RES.AUTO_RES_OK || autoRes == AUTO_RES.AUTO_RES_NORMAL)
                sendOBD2CMD("41 01 82 07 65 04 >");
            if(autoRes == AUTO_RES.AUTO_RES_ERROR)
                sendOBD2CMD("CAN ERROR >");
        }
        if(res.equals("03")){
            if(autoRes == AUTO_RES.AUTO_RES_OK || autoRes == AUTO_RES.AUTO_RES_NORMAL)
                sendOBD2CMD("43 00 43 01 33 00 00 >");
            if(autoRes == AUTO_RES.AUTO_RES_ERROR)
                sendOBD2CMD("CAN ERROR >");
        }
    }
    private void setPidsSupported(String buffer){
        byte[] pidSupported = null;
        StringBuilder flags = new StringBuilder();
        String buf = buffer.toString();
        buf = buf.trim();
        buf = buf.replace("\t", "");
        buf = buf.replace(" ", "");
        buf = buf.replace(">", "");
        pidSupported = buf.getBytes();
        if(buf.indexOf("4100") == 0){
            for(int i = 0; i < 8; i++ ){
                String tmp = buf.substring(i+4, i+5);
                int data = Integer.valueOf(tmp, 16).intValue();
//                String retStr = Integer.toBinaryString(data);
                if ((data & 0x08) == 0x08){
                    flags.append("1");
                }else{
                    flags.append( "0");
                }

                if ((data  & 0x04) == 0x04){
                    flags.append("1");
                }else{
                    flags.append( "0");
                }

                if ((data  & 0x02) == 0x02){
                    flags.append("1");
                }else{
                    flags.append( "0");
                 }

                if ((data  & 0x01) == 0x01){
                    flags.append("1");
                }else{
                    flags.append( "0");
                }
            }

            StringBuilder supportedPID = new StringBuilder();
            supportedPID.append("支持PID: ");
            StringBuilder unSupportedPID = new StringBuilder();
            unSupportedPID.append("不支持PID: ");
            for(int j = 0; j < flags.length(); j++){
                if(flags.charAt(j) == '1'){
                    supportedPID.append(" "+ PIDS[j] + " ");
                }else{
                    unSupportedPID.append(" "+ PIDS[j] + " ");
                }
            }
            supportedPID.append("\n");

            mSupportedPidsTxt.setText(supportedPID.toString() + unSupportedPID.toString());
        }else{
            return;
        }
    }

    private void sendOBD2CMD(){
        if(mOBD2MonitorService.getState() != OBD2MonitorService.STATE_CONNECTED){
            Toast.makeText(this,R.string.bt_not_available,
                    Toast.LENGTH_LONG).show();
        }
        String strCMD = mInputOBD2CMDEditTxt.getText().toString();
        if(strCMD.equals("")){
            Toast.makeText(this,R.string.please_input_cmd,
                    Toast.LENGTH_LONG).show();
            return;
        }
        strCMD += '\r';
        mCmdAndRes.append(" > Send: "+ strCMD);
        mCmdAndRes.append('\n');
        mResponseMessageTxt.setText(mCmdAndRes.toString());
//        byte[] byteCMD =new byte[11];
//        for(int i=0;i<11;i++)
//        {
//            byteCMD[i]= (byte)(Integer.parseInt( strCMD.substring(i * 2, i*2 + 1)));
//        }
        byte[] byteCMD = strCMD.getBytes();
        mOBD2MonitorService.write(byteCMD);
//        writeOBD2MonitorLog("Send: "+ strCMD);
    }

    private void sendOBD2CMD(String sendMsg){
        if(mOBD2MonitorService.getState() != OBD2MonitorService.STATE_CONNECTED){
            Toast.makeText(this,R.string.bt_not_available,
                    Toast.LENGTH_LONG).show();
        }
        String strCMD = sendMsg;
        strCMD += '\r';
        mCmdAndRes.append(" > Send: "+ strCMD);
        mCmdAndRes.append('\n');
        mResponseMessageTxt.setText(mCmdAndRes.toString());
        byte[] byteCMD = strCMD.getBytes();
        mOBD2MonitorService.write(byteCMD);
//        writeOBD2MonitorLog("Send: "+ strCMD);
    }

    private void selectDevice(){
        Intent devicesIntent = new Intent(OBD2MonitorMainActivity.this, OBD2MonitorDevicesActivity.class);
        startActivityForResult(devicesIntent, REQUEST_CONNECT_DEVICE_SECURE);
    }

    private void connectDevice(Intent data, boolean secure){
        // get bluetooth mac address
        String address = data.getExtras().getString(OBD2MonitorDevicesActivity.EXTRA_DEVICE_ADDRESS);
        // Get the bluetooth Device object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mOBD2MonitorService.connect(device, secure);
    }

    private void disconncetDevice(){
        if(mOBD2MonitorService != null){
            mOBD2MonitorService.stop();
        }
    }

    // Create LogFile
    public static void writeOBD2MonitorLog(String content) {
        try {
            File rooDir = new File(Environment.getExternalStorageDirectory(), DIR_NAME_OBD2_MONITOR);
            if (!rooDir.exists()) {
                rooDir.mkdirs();
            }
            File logFile = new File(rooDir, FILE_NAME_OBD2_MONITOR_LOG);
            if(!logFile.exists()){
                logFile.createNewFile();
            }
            if(logFile.canWrite()){
                SimpleDateFormat stime = new SimpleDateFormat(
                        "yyyy-MM-dd hh:mm:ss ");
                RandomAccessFile Dfile = new RandomAccessFile(logFile, "rw");
                String contents = stime.format("==" + new Date()) + "->" + content
                        + "\r\n";
                Dfile.seek(Dfile.length());
                Dfile.write(contents.getBytes("UTF-8"));
                Dfile.close();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();

        } catch (IOException e) {
            e.printStackTrace();

        }

    }

    @Override
    public void onActivityResult(int requstCode, int resultCode, Intent data){
        switch(requstCode){
            case REQUEST_CONNECT_DEVICE_SECURE:
                if(resultCode == Activity.RESULT_OK){
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                if(resultCode == Activity.RESULT_OK){
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                if(resultCode == Activity.RESULT_OK){
                    setupOBDMonitor();
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onStart(){
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else {
            if (mOBD2MonitorService == null)
                setupOBDMonitor();
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected synchronized void onResume(){
        super.onResume();
        if (mOBD2MonitorService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mOBD2MonitorService.getState() == OBD2MonitorService.STATE_NONE) {
                // Start the Bluetooth chat services
                mOBD2MonitorService.start();
            }
        }
        if(mCmdAndRes.length() > 0){
            mCmdAndRes.delete(0, mCmdAndRes.length());
        }
    }

    @Override
    protected void onStop(){
        super.onStop();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(mOBD2MonitorService != null){
            mOBD2MonitorService.stop();
        }
        if(mCmdAndRes.length() > 0){
            mCmdAndRes.delete(0, mCmdAndRes.length());
        }
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_obd2_monitor_main, menu);
        mItemSetting = menu.findItem(R.id.menu_settings);
        mItemQuit = menu.findItem(R.id.menu_quit);
        mItemHelp = menu.findItem(R.id.menu_help);
        mItemAutoResOK = menu.findItem(R.id.menu_res_ok);
        mItemAutoResNormal = menu.findItem(R.id.menu_res_normal);
        mItemAutoResError = menu.findItem(R.id.menu_res_error);
		return true;
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menu_settings:
                Toast.makeText(this, "Menu_Setting_Clicked", Toast.LENGTH_LONG).show();
                break;
            case R.id.menu_quit:
                finish();
                break;
            case R.id.menu_help:
                Intent helpIntent = new Intent();
                helpIntent.setClass(this,OBD2MonitorHelpActivity.class);
                startActivity(helpIntent);
                break;
            case R.id.menu_res_ok:
                autoRes = AUTO_RES.AUTO_RES_OK;
                break;
            case R.id.menu_res_normal:
                autoRes = AUTO_RES.AUTO_RES_NORMAL;
                break;
            case R.id.menu_res_error:
                autoRes = AUTO_RES.AUTO_RES_ERROR;
                break;
        }
//        return super.onOptionsItemSelected(item);
        return true;
    }
}
