package com.flyq.obd2monitor;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ListView;

/**
 * Created by qufei on 13/10/17.
 */
public class OBD2MonitorHelpActivity extends Activity {
    private ListView mOBD2Protocols = null;
    private GridView mOBD2Commands = null;
    @Override
    protected void onCreate(Bundle savaInstanceState){
        super.onCreate(savaInstanceState);
        setContentView(R.layout.activity_obd2_monitor_help);

        mOBD2Protocols = (ListView)findViewById(R.id.obd2_protocol_listView);
        String[] mItems = getResources().getStringArray(R.array.protocols);
        // 建立Adapter并且绑定数据源
        ArrayAdapter<String> listAdapter = new ArrayAdapter<String>(this, R.layout.device_name, mItems);
        //绑定 Adapter到控件
        mOBD2Protocols.setAdapter(listAdapter);

        mOBD2Commands = (GridView)findViewById(R.id.obd2_cmd_gridView);
        String[] mCmdItmes = getResources().getStringArray(R.array.OBD2_Commands);
        ArrayAdapter<String> grideAdapter = new ArrayAdapter<String>(this, R.layout.gride_name, mCmdItmes);
        mOBD2Commands.setAdapter(grideAdapter);
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    @Override
    protected void onStop(){
        super.onStop();
    }
}
