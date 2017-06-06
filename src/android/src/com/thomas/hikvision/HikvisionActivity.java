/**
 * <p>HikvisonActivity Class</p>
 * @author zhuzhenlei 2014-7-17
 * @version V1.0
 * @modificationHistory
 * @modify by user:
 * @modify by reason:
 */
package com.thomas.hikvision;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONArray;
import com.hikvision.netsdk.ExceptionCallBack;
import com.hikvision.netsdk.HCNetSDK;
import com.hikvision.netsdk.INT_PTR;
import com.hikvision.netsdk.NET_DVR_DEVICEINFO_V30;

import java.util.List;


/**
 * <pre>
 *  ClassName  HikvisonActivity Class
 * </pre>
 *
 * @author zhuzhenlei
 * @version V1.0
 * @modificationHistory
 */
public class HikvisionActivity extends Activity {
	private Button m_oback = null;
	private FrameLayout surfaceView =null;
	private Button tv_title;

	private NET_DVR_DEVICEINFO_V30 m_oNetDvrDeviceInfoV30 = null;

	private int m_iLogID = -1; // return by NET_DVR_Login_v30
	private int m_iPlaybackID = -1; // return by NET_DVR_PlayBackByTime

	private int m_iPort = -1; // play port
	private int m_iStartChan = 0; // start channel no
//	private int m_iChanNum = 0; // channel number
	private static PlaySurfaceView[] playView ;

	private final String TAG = "HikvisonActivity";


	private List<CanteenBean> canteenBeanList;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		CrashUtil crashUtil = CrashUtil.getInstance();
		crashUtil.init(this);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);//去掉标题栏 第一种方法
		setContentView(getId("activity_hikvision","layout"));

		if (!initeSdk()) {
			this.finish();
			return;
		}
		if (!initeActivity()) {
			this.finish();
			return;
		}
		initPopupWindow();
	}

	private String[] datas;
	private void getParams(){
		Intent intent = getIntent();
		String array = intent.getStringExtra("array");
		canteenBeanList = JSONArray.parseArray(array,CanteenBean.class);
		int size = canteenBeanList.size();
		datas = new String[size];
		for (int i = 0;i<size;i++){
			datas[i] = canteenBeanList.get(i).getCanteenName();
		}
		changeCanteen(0);
	}
	private int winNum;
	private int cameraNum;
	private String ip ;
	private String port ;
	private String userName ;
	private String password ;
	private String title;
	private void changeCanteen(int index){
		CanteenBean canteenBean = canteenBeanList.get(index);
		winNum = Integer.valueOf(canteenBean.getWinNum());
		cameraNum = canteenBean.getCameraNum();
		ip = canteenBean.getPortId();
		port = canteenBean.getAppPortNo();
		userName = canteenBean.getUserName();
		password = canteenBean.getPwd();
		title = canteenBean.getCanteenName();
		if(winNum<cameraNum){
			cameraNum = winNum;
		}
	}
	private PopupWindow window;
	private ListView titleListView;
	private void initPopupWindow() {

		// 构建一个popupwindow的布局
		View popupView = getLayoutInflater().inflate(getIdTypeLayout("popupwindow_list"), null);

//        // 为了演示效果，简单的设置了一些数据，实际中大家自己设置数据即可，相信大家都会。
//        ListView lsvMore = (ListView) popupView.findViewById(R.id.lsvMore);
		titleListView = (ListView) popupView.findViewById(getIdTypeId("hikvision_lv_title"));
//		titleListView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, datas));
		titleListView.setAdapter(new MyListAdapter());
//		titleListView.setAdapter(new MyListAdapter());
		titleListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
				window.dismiss();
				tv_title.setText(datas[position]);
				stopMultiPreview();
				// whether we have logout
				HCNetSDK.getInstance().NET_DVR_Logout_V30(m_iLogID);
				surfaceView.removeAllViews();
				for (int i = 0;i<cameraNum;i++){
					playView[i] = null;
				}
				m_iLogID = -1;
				changeCanteen(position);
				login();
				ChangeSingleSurFace();

			}
		});
		// 创建PopupWindow对象，指定宽度和高度
		window = new PopupWindow(popupView,  LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		// 设置动画
//        window.setAnimationStyle(R.style.popup_window_anim);
		// 设置背景颜色
		window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#F8F8F8")));
		//  设置可以获取焦点
		window.setFocusable(true);
		// 设置可以触摸弹出框以外的区域
		window.setOutsideTouchable(true);
		// 更新popupwindow的状态
		window.update();
	}
	private int getIdTypeLayout(String name){
		return getId(name,"layout");
	}
	private int getIdTypeId(String name){
		return getId(name,"id");
	}
	private int getId(String idName, String type){
		return getResources().getIdentifier(idName, type,getPackageName());
	}
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt("m_iPort", m_iPort);
		super.onSaveInstanceState(outState);
		Log.i(TAG, "onSaveInstanceState");
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		m_iPort = savedInstanceState.getInt("m_iPort");
		super.onRestoreInstanceState(savedInstanceState);
		Log.i(TAG, "onRestoreInstanceState");
	}

	private boolean initeSdk() {
		// init net sdk
		if (!HCNetSDK.getInstance().NET_DVR_Init()) {
			Log.e(TAG, "HCNetSDK init is failed!");
			return false;
		}
		HCNetSDK.getInstance().NET_DVR_SetLogToFile(3, "/mnt/sdcard/sdklog/", true);
		return true;
	}

	// GUI init
	private boolean initeActivity() {
		getParams();
		findViews();
		setListeners();
		login();
		ChangeSingleSurFace();
		return true;
	}
	// get controller instance
	private void findViews() {
		surfaceView = (FrameLayout) findViewById(getId("Sur_Player","id"));
		m_oback= (Button) findViewById(getId("btn_back","id"));
		tv_title= (Button) findViewById(getId("tv_title","id"));
		tv_title.setText(title);
	}

	// listen
	private void setListeners() {
		m_oback.setOnClickListener(Back_Listener);
		tv_title.setOnClickListener(ChangeTitle_Listener);
	}

	private void ChangeSingleSurFace() {
		DisplayMetrics metric = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metric);
		playView= new PlaySurfaceView[cameraNum];
		for (int i = 0; i < cameraNum; i++) {
			if (playView[i] == null) {
				playView[i] = new PlaySurfaceView(this,i);
				playView[i].setParam(metric.widthPixels);
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
						FrameLayout.LayoutParams.WRAP_CONTENT);
				params.topMargin = (i / 2) * playView[i].getM_iHeight();
				params.leftMargin = (i % 2) * playView[i].getM_iWidth();
				surfaceView.addView(playView[i],params);
				playView[i].setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						PlaySurfaceView playSurfaceView = (PlaySurfaceView) v;
						int index = playSurfaceView.getIndex();
						if(playSurfaceView.getM_iPreviewHandle()<0){
							return;
						}
						Intent intent = new Intent(HikvisionActivity.this,HikvisionSingleActivity.class);
						intent.putExtra("m_iLogID",m_iLogID);
						intent.putExtra("ip",ip);
						intent.putExtra("port",port);
						intent.putExtra("userName",userName);
						intent.putExtra("password",password);
						intent.putExtra("index",index);
						intent.putExtra("m_iStartChan",m_iStartChan);
						stopMultiPreview();
						startActivity(intent);
					}
				});
				playView[i].setPlaySurfaceViewCallBack(new PlaySurfaceViewCallBack() {
					@Override
					public void surfaceCreated(SurfaceHolder surfaceHolder, int index) {
						playView[index].startPreview(m_iLogID, m_iStartChan + index);
					}
				});
			}
		}
	}


	private void login(){
		try {
			if (m_iLogID < 0) {
				// login on the device
				m_iLogID = loginNormalDevice();
				if (m_iLogID < 0) {
					Log.e(TAG, "This device logins failed!");
					Toast toast = Toast.makeText(HikvisionActivity.this,"登录失败！",Toast.LENGTH_LONG);
					toast.setGravity(Gravity.CENTER, 0, 0);
					toast.show();
					return;
				} else {
					System.out.println("m_iLogID=" + m_iLogID);
				}
				// get instance of exception callback and set
				ExceptionCallBack oexceptionCbf = getExceptiongCbf();
				if (oexceptionCbf == null) {
					Log.e(TAG, "ExceptionCallBack object is failed!");
					return;
				}
				if (!HCNetSDK.getInstance().NET_DVR_SetExceptionCallBack(oexceptionCbf)) {
					Log.e(TAG, "NET_DVR_SetExceptionCallBack is failed!");
					return;
				}
				Log.i(TAG, "Login sucess ****************************1***************************");
			} else {
				// whether we have logout
				if (!HCNetSDK.getInstance().NET_DVR_Logout_V30(m_iLogID)) {
					Log.e(TAG, " NET_DVR_Logout is failed!");
					return;
				}
				m_iLogID = -1;
			}
		} catch (Exception err) {
			Log.e(TAG, "error: " + err.toString());
		}
	}


	private int loginNormalDevice() {
		// get instance
		m_oNetDvrDeviceInfoV30 = new NET_DVR_DEVICEINFO_V30();
		if (null == m_oNetDvrDeviceInfoV30) {
			Log.e(TAG, "HKNetDvrDeviceInfoV30 new is failed!");
			return -1;
		}
		String strIP = ip;
		int nPort = Integer.parseInt(port);
		String strUser = userName;
		String strPsd = password;
		// call NET_DVR_Login_v30 to login on, port 8000 as default
		int iLogID = HCNetSDK.getInstance().NET_DVR_Login_V30(strIP, nPort, strUser, strPsd, m_oNetDvrDeviceInfoV30);

		if (iLogID < 0) {
			Log.e(TAG, "NET_DVR_Login is failed!Err:" + HCNetSDK.getInstance().NET_DVR_GetLastError());
			return -1;
		}


		if (m_oNetDvrDeviceInfoV30.byChanNum > 0) {
			m_iStartChan = m_oNetDvrDeviceInfoV30.byStartChan;
//			m_iChanNum = m_oNetDvrDeviceInfoV30.byChanNum;
		} else if (m_oNetDvrDeviceInfoV30.byIPChanNum > 0) {
			m_iStartChan = m_oNetDvrDeviceInfoV30.byStartDChan;
//			m_iChanNum = 1;
//			m_iChanNum = m_oNetDvrDeviceInfoV30.byIPChanNum;
			/*
						 * m_oNetDvrDeviceInfoV30.byIPChanNum +
						 * m_oNetDvrDeviceInfoV30.byHighDChanNum * 256
						 */
		}
//		NET_DVR_IPPARACFG_V40 netDvrIpparacfgV40 = new NET_DVR_IPPARACFG_V40();
//		HCNetSDK.getInstance().NET_DVR_GetDVRConfig(iLogID,HCNetSDK.NET_DVR_GET_IPPARACFG_V40,0xFFFFFFFF,netDvrIpparacfgV40);
//		int dwDChanNum = netDvrIpparacfgV40.dwDChanNum;
//		int dwGroupNum = netDvrIpparacfgV40.dwGroupNum;
//		NET_DVR_IPCHANINFO[] netDvrIpchaninfos = netDvrIpparacfgV40.struIPChanInfo;
//		m_iChanNum = 0;
//		for (int i = 0;i<dwDChanNum;i++){
//			if (netDvrIpchaninfos[i].byEnable == 1){
//				m_iChanNum++;
//			}
//		}
		Log.i(TAG, "NET_DVR_Login is Successful!");
		return iLogID;
	}


	private void stopMultiPreview() {
		for (int i = 0; i < cameraNum; i++) {
			playView[i].stopPreview();
		}
	}

	public static void Test_XMLAbility(int iUserID) {
		byte[] arrayOutBuf = new byte[64 * 1024];
		INT_PTR intPtr = new INT_PTR();
		String strInput = new String("<AlarmHostAbility version=\"2.0\"></AlarmHostAbility>");
		byte[] arrayInBuf = new byte[8 * 1024];
		arrayInBuf = strInput.getBytes();
		if (!HCNetSDK.getInstance().NET_DVR_GetXMLAbility(iUserID, HCNetSDK.DEVICE_ABILITY_INFO, arrayInBuf,
				strInput.length(), arrayOutBuf, 64 * 1024, intPtr)) {
			System.out.println("get DEVICE_ABILITY_INFO faild!" + " err: "
					+ HCNetSDK.getInstance().NET_DVR_GetLastError());
		} else {
			System.out.println("get DEVICE_ABILITY_INFO succ!");
		}
	}

	private ExceptionCallBack getExceptiongCbf() {
		ExceptionCallBack oExceptionCbf = new ExceptionCallBack() {
			public void fExceptionCallBack(int iType, int iUserID, int iHandle) {
				System.out.println("recv exception, type:" + iType);
			}
		};
		return oExceptionCbf;
	}

	private OnClickListener ChangeTitle_Listener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			window.showAsDropDown(tv_title);
		}
	};

	private OnClickListener Back_Listener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			finishThisActivity();
		}
	};

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		finishThisActivity();
	}

	private void finishThisActivity(){
		stopMultiPreview();
		// whether we have logout
		HCNetSDK.getInstance().NET_DVR_Logout_V30(m_iLogID);
		surfaceView.removeAllViews();
		for (int i = 0;i<cameraNum;i++){
			playView[i] = null;
		}
		m_iLogID = -1;
		finish();
	}

	class MyListAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return datas.length;
		}

		@Override
		public Object getItem(int position) {
			return datas[position];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView textView;
			if (convertView == null) {
				textView = new TextView(HikvisionActivity.this);
				textView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT));//设置TextView对象布局
				textView.setPadding(10,20,10,20);
				textView.setTextSize(20);
				textView.setGravity(Gravity.CENTER);

			}
			else {
				textView = (TextView) convertView;
			}
			textView.setText(datas[position]);
			return textView;
		}
	}
}