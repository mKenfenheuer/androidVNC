/* 
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

//
// CanvasView is the Activity for showing VNC Desktop.
//
package com.kenfenheuer.vncclient;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.antlersoft.android.bc.BCFactory;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.PointF;
import android.media.Image;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

public class VncCanvasActivity extends Activity implements View.OnGenericMotionListener {

	/**
	 * @author Michael A. MacDonald
	 */

	private final static String TAG = "VncCanvasActivity";

	AbstractInputHandler inputHandler;

	VncCanvas vncCanvas;

	VncDatabase database;

	private MenuItem[] inputModeMenuItems;
	private AbstractInputHandler inputModeHandlers[];
	private ConnectionBean connection;
	private boolean trackballButtonDown;
	private static final int inputModeIds[] = {
			R.id.itemInputFitToScreen,
			R.id.itemInputMouse,
			R.id.itemInputHardwareMouse,};
	private boolean kbdVisible = false;
	Panner panner;

	@Override
	public void onCreate(Bundle icicle) {

		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		database = new VncDatabase(this);

		Intent i = getIntent();
		connection = new ConnectionBean();
		Uri data = i.getData();
		if ((data != null) && (data.getScheme().equals("vnc"))) {
			String host = data.getHost();
			// This should not happen according to Uri contract, but bug introduced in Froyo (2.2)
			// has made this parsing of host necessary
			int index = host.indexOf(':');
			int port;
			if (index != -1) {
				try {
					port = Integer.parseInt(host.substring(index + 1));
				} catch (NumberFormatException nfe) {
					port = 0;
				}
				host = host.substring(0, index);
			} else {
				port = data.getPort();
			}
			if (host.equals(VncConstants.CONNECTION)) {
				if (connection.Gen_read(database.getReadableDatabase(), port)) {
					MostRecentBean bean = androidVNC.getMostRecent(database.getReadableDatabase());
					if (bean != null) {
						bean.setConnectionId(connection.get_Id());
						bean.Gen_update(database.getWritableDatabase());
					}
				}
			} else {
				connection.setAddress(host);
				connection.setNickname(connection.getAddress());
				connection.setPort(port);
				List<String> path = data.getPathSegments();
				if (path.size() >= 1) {
					connection.setColorModel(path.get(0));
				}
				if (path.size() >= 2) {
					connection.setPassword(path.get(1));
				}
				connection.save(database.getWritableDatabase());
			}
		} else {

			Bundle extras = i.getExtras();

			if (extras != null) {
				connection.Gen_populate((ContentValues) extras
						.getParcelable(VncConstants.CONNECTION));
			}
			if (connection.getPort() == 0)
				connection.setPort(5900);

			// Parse a HOST:PORT entry
			String host = connection.getAddress();
			if (host.indexOf(':') > -1) {
				String p = host.substring(host.indexOf(':') + 1);
				try {
					connection.setPort(Integer.parseInt(p));
				} catch (Exception e) {
				}
				connection.setAddress(host.substring(0, host.indexOf(':')));
			}
		}
		setContentView(R.layout.canvas);

		vncCanvas = (VncCanvas) findViewById(R.id.vnc_canvas);

		vncCanvas.initializeVncCanvas(connection, new Runnable() {
			public void run() {
				setModes();
			}
		});
		vncCanvas.setOnGenericMotionListener(this);
		panner = new Panner(this, vncCanvas.handler);

		AbstractScaling.getById(1).setScaleTypeForActivity(
				this);

		inputHandler = getInputHandlerById(R.id.itemInputFitToScreen);




		Button btnEsc = findViewById(R.id.btnEsc);
		btnEsc.setBackgroundResource(0);
		Button btnTab = findViewById(R.id.btnTab);
		btnTab.setBackgroundResource(0);
		Button btnCtrl = findViewById(R.id.btnCtrl);
		btnCtrl.setBackgroundResource(0);
		Button btnAlt = findViewById(R.id.btnAlt);
		btnAlt.setBackgroundResource(0);
		Button btnDel = findViewById(R.id.btnDel);
		btnDel.setBackgroundResource(0);
		Button btnMenu = findViewById(R.id.btnMenu);
		btnMenu.setBackgroundResource(0);
		Button btnDrag = findViewById(R.id.btnDrag);
		btnDrag.setBackgroundResource(0);
		ImageButton btnKbd = findViewById(R.id.btnKbd);

		View.OnClickListener buttonBarClickListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(v.getBackground() == null)
				{
					v.setBackgroundResource(R.drawable.btn_active_background);
				}
				else
				{
					v.setBackgroundResource(0);
				}
			}
		};

		btnEsc.setOnClickListener(buttonBarClickListener);
		btnTab.setOnClickListener(buttonBarClickListener);
		btnCtrl.setOnClickListener(buttonBarClickListener);
		btnAlt.setOnClickListener(buttonBarClickListener);
		btnDel.setOnClickListener(buttonBarClickListener);
		btnDrag.setOnClickListener(buttonBarClickListener);
		btnMenu.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

			}
		});
		btnKbd.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				kbdVisible = true;
				InputMethodManager inputMethodManager =
						(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
				inputMethodManager.toggleSoftInputFromWindow(
						vncCanvas.getApplicationWindowToken(),
						InputMethodManager.SHOW_FORCED, 0);
			}
		});

	}

	/**
	 * Set modes on start to match what is specified in the ConnectionBean;
	 * color mode (already done) scaling, input mode
	 */
	void setModes() {
		AbstractInputHandler handler = getInputHandlerByName(connection
				.getInputMode());
		AbstractScaling.getByScaleType(connection.getScaleMode())
				.setScaleTypeForActivity(this);
		this.inputHandler = handler;
		showPanningState();
	}

	ConnectionBean getConnection() {
		return connection;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case R.layout.entertext:
			return new EnterTextDialog(this);
		}
		// Default to meta key dialog
		return new MetaKeyDialog(this);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onPrepareDialog(int, android.app.Dialog)
	 */
	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
		if (dialog instanceof ConnectionSettable)
			((ConnectionSettable) dialog).setConnection(connection);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// ignore orientation/keyboard change
		super.onConfigurationChanged(newConfig);
	}

	@Override
	protected void onStop() {
		vncCanvas.disableRepaints();
		super.onStop();
	}

	@Override
	protected void onRestart() {
		vncCanvas.enableRepaints();
		super.onRestart();
	}

	/** {@inheritDoc} */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.vnccanvasactivitymenu, menu);

		Menu inputMenu = menu.findItem(R.id.itemInputMode).getSubMenu();

		inputModeMenuItems = new MenuItem[inputModeIds.length];
		for (int i = 0; i < inputModeIds.length; i++) {
			inputModeMenuItems[i] = inputMenu.findItem(inputModeIds[i]);
		}
		updateInputMenu();
		menu.findItem(R.id.itemFollowMouse).setChecked(
				connection.getFollowMouse());
		menu.findItem(R.id.itemFollowPan).setChecked(connection.getFollowPan());
		return true;
	}

	/**
	 * Change the input mode sub-menu to reflect change in scaling
	 */
	void updateInputMenu() {
		if (inputModeMenuItems == null || vncCanvas.scaling == null) {
			return;
		}
		for (MenuItem item : inputModeMenuItems) {
			item.setEnabled(vncCanvas.scaling
					.isValidInputMode(item.getItemId()));
			if (getInputHandlerById(item.getItemId()) == inputHandler)
				item.setChecked(true);
		}
	}

	/**
	 * If id represents an input handler, return that; otherwise return null
	 *
	 * @param id
	 * @return
	 */
	AbstractInputHandler getInputHandlerById(int id) {
		if (inputModeHandlers == null) {
			inputModeHandlers = new AbstractInputHandler[inputModeIds.length];
		}
		for (int i = 0; i < inputModeIds.length; ++i) {
			if (inputModeIds[i] == id) {
				if (inputModeHandlers[i] == null) {
					switch (id) {
					case R.id.itemInputFitToScreen:
						inputModeHandlers[i] = new FitToScreenMode();
						break;
					case R.id.itemInputMouse:
						inputModeHandlers[i] = new MouseMode();
						break;
					case R.id.itemInputHardwareMouse:
						inputModeHandlers[i] = new HardwareMouseMode();
						break;
					}
				}
				return inputModeHandlers[i];
			}
		}
		return null;
	}

	AbstractInputHandler getInputHandlerByName(String name) {
		AbstractInputHandler result = null;
		for (int id : inputModeIds) {
			AbstractInputHandler handler = getInputHandlerById(id);
			if (handler.getName().equals(name)) {
				result = handler;
				break;
			}
		}
		if (result == null) {
			result = getInputHandlerById(R.id.itemInputFitToScreen);
		}
		return result;
	}

	int getModeIdFromHandler(AbstractInputHandler handler) {
		for (int id : inputModeIds) {
			if (handler == getInputHandlerById(id))
				return id;
		}
		return R.id.itemInputFitToScreen;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		vncCanvas.afterMenu = true;
		switch (item.getItemId()) {
		case R.id.itemInfo:
			vncCanvas.showConnectionInfo();
			return true;
		case R.id.itemSpecialKeys:
			showDialog(R.layout.metakey);
			return true;
		case R.id.itemColorMode:
			selectColorModel();
			return true;
		case R.id.itemCenterMouse:
			vncCanvas.warpMouse(vncCanvas.absoluteXPosition
					+ vncCanvas.getVisibleWidth() / 2,
					vncCanvas.absoluteYPosition + vncCanvas.getVisibleHeight()
							/ 2);
			return true;
		case R.id.itemDisconnect:
			vncCanvas.closeConnection();
			finish();
			return true;
		case R.id.itemEnterText:
			showDialog(R.layout.entertext);
			return true;
		case R.id.itemCtrlAltDel:
			vncCanvas.sendMetaKey(MetaKeyBean.keyCtrlAltDel);
			return true;
		case R.id.itemFollowMouse:
			boolean newFollow = !connection.getFollowMouse();
			item.setChecked(newFollow);
			connection.setFollowMouse(newFollow);
			if (newFollow) {
				vncCanvas.panToMouse();
			}
			connection.save(database.getWritableDatabase());
			return true;
		case R.id.itemFollowPan:
			boolean newFollowPan = !connection.getFollowPan();
			item.setChecked(newFollowPan);
			connection.setFollowPan(newFollowPan);
			connection.save(database.getWritableDatabase());
			return true;
		case R.id.itemArrowLeft:
			vncCanvas.sendMetaKey(MetaKeyBean.keyArrowLeft);
			return true;
		case R.id.itemArrowUp:
			vncCanvas.sendMetaKey(MetaKeyBean.keyArrowUp);
			return true;
		case R.id.itemArrowRight:
			vncCanvas.sendMetaKey(MetaKeyBean.keyArrowRight);
			return true;
		case R.id.itemArrowDown:
			vncCanvas.sendMetaKey(MetaKeyBean.keyArrowDown);
			return true;
		case R.id.itemSendKeyAgain:
			sendSpecialKeyAgain();
			return true;
		case R.id.itemOpenDoc:
			Utils.showDocumentation(this);
			return true;
		default:
			AbstractInputHandler input = getInputHandlerById(item.getItemId());
			if (input != null) {
				inputHandler = input;
				connection.setInputMode(input.getName());
				item.setChecked(true);
				showPanningState();
				connection.save(database.getWritableDatabase());
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	private MetaKeyBean lastSentKey;

	private void sendSpecialKeyAgain() {
		if (lastSentKey == null
				|| lastSentKey.get_Id() != connection.getLastMetaKeyId()) {
			ArrayList<MetaKeyBean> keys = new ArrayList<MetaKeyBean>();
			Cursor c = database.getReadableDatabase().rawQuery(
					MessageFormat.format("SELECT * FROM {0} WHERE {1} = {2}",
							MetaKeyBean.GEN_TABLE_NAME,
							MetaKeyBean.GEN_FIELD__ID, connection
									.getLastMetaKeyId()),
					MetaKeyDialog.EMPTY_ARGS);
			MetaKeyBean.Gen_populateFromCursor(c, keys, MetaKeyBean.NEW);
			c.close();
			if (keys.size() > 0) {
				lastSentKey = keys.get(0);
			} else {
				lastSentKey = null;
			}
		}
		if (lastSentKey != null)
			vncCanvas.sendMetaKey(lastSentKey);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (isFinishing()) {
			vncCanvas.closeConnection();
			vncCanvas.onDestroy();
			database.close();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent evt) {
		if (keyCode == KeyEvent.KEYCODE_MENU)
			return super.onKeyDown(keyCode, evt);

		if(keyCode == KeyEvent.KEYCODE_BACK) {
			if(!kbdVisible)
            	((FitToScreenMode) inputHandler).nextRight = true;
			else{
				InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(vncCanvas.getApplicationWindowToken(), 0);
			}
		}

		return inputHandler.onKeyDown(keyCode, evt);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent evt) {
		if (keyCode == KeyEvent.KEYCODE_MENU)
			return super.onKeyUp(keyCode, evt);

		return inputHandler.onKeyUp(keyCode, evt);
	}

	public void showPanningState() {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see android.app.Activity#onTrackballEvent(android.view.MotionEvent)
	 */
	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			trackballButtonDown = true;
			break;
		case MotionEvent.ACTION_UP:
			trackballButtonDown = false;
			break;
		}
		return inputHandler.onTrackballEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return inputHandler.onTouchEvent(event);
	}

	@Override
	public boolean onGenericMotion(View view, MotionEvent motionEvent) {
		return inputHandler.onGenericMotion(motionEvent);
	}

	private void selectColorModel() {
		// Stop repainting the desktop
		// because the display is composited!
		vncCanvas.disableRepaints();

		String[] choices = new String[COLORMODEL.values().length];
		int currentSelection = -1;
		for (int i = 0; i < choices.length; i++) {
			COLORMODEL cm = COLORMODEL.values()[i];
			choices[i] = cm.toString();
			if (vncCanvas.isColorModel(cm))
				currentSelection = i;
		}

		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		ListView list = new ListView(this);
		list.setAdapter(new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_checked, choices));
		list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		list.setItemChecked(currentSelection, true);
		list.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				dialog.dismiss();
				COLORMODEL cm = COLORMODEL.values()[arg2];
				vncCanvas.setColorModel(cm);
				connection.setColorModel(cm.nameString());
				connection.save(database.getWritableDatabase());
				Toast.makeText(VncCanvasActivity.this,
						"Updating Color Model to " + cm.toString(),
						Toast.LENGTH_SHORT).show();
			}
		});
		dialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface arg0) {
				Log.i(TAG, "Color Model Selector dismissed");
				// Restore desktop repaints
				vncCanvas.enableRepaints();
			}
		});
		dialog.setContentView(list);
		dialog.show();
	}

	float panTouchX, panTouchY;

	/**
	 * Pan based on touch motions
	 *
	 * @param event
	 */
	private boolean pan(MotionEvent event) {
		float curX = event.getX();
		float curY = event.getY();
		int dX = (int) (panTouchX - curX);
		int dY = (int) (panTouchY - curY);

		return vncCanvas.pan(dX, dY);
	}

	boolean defaultKeyDownHandler(int keyCode, KeyEvent evt) {
		if (vncCanvas.processLocalKeyEvent(keyCode, evt))
			return true;
		return super.onKeyDown(keyCode, evt);
	}

	boolean defaultKeyUpHandler(int keyCode, KeyEvent evt) {
		if (vncCanvas.processLocalKeyEvent(keyCode, evt))
			return true;
		return super.onKeyUp(keyCode, evt);
	}

	boolean touchPan(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			panTouchX = event.getX();
			panTouchY = event.getY();
			break;
		case MotionEvent.ACTION_MOVE:
			pan(event);
			panTouchX = event.getX();
			panTouchY = event.getY();
			break;
		case MotionEvent.ACTION_UP:
			pan(event);
			break;
		}
		return true;
	}

	private static int convertTrackballDelta(double delta) {
		return (int) Math.pow(Math.abs(delta) * 6.01, 2.5)
				* (delta < 0.0 ? -1 : 1);
	}

	boolean trackballMouse(MotionEvent evt) {
		int dx = convertTrackballDelta(evt.getX());
		int dy = convertTrackballDelta(evt.getY());

		evt.offsetLocation(vncCanvas.mouseX + dx - evt.getX(), vncCanvas.mouseY
				+ dy - evt.getY());

		if (vncCanvas.processPointerEvent(evt, trackballButtonDown)) {
			return true;
		}
		return VncCanvasActivity.super.onTouchEvent(evt);
	}

	static final String FIT_SCREEN_NAME = "FIT_SCREEN";

	/**
	 * In fit-to-screen mode, no panning. Trackball and touchscreen work as
	 * mouse.
	 *
	 * @author Michael A. MacDonald
	 *
	 */
	public class FitToScreenMode implements AbstractInputHandler {
		private DPadMouseKeyHandler keyHandler = new DPadMouseKeyHandler(VncCanvasActivity.this, vncCanvas.handler);

		private float[] mousePos = new float[2];
        public boolean nextRight = false;
        private float[] touchBeginPos = new float[2];
        private float[] lastTouchPos = new float[2];

		private long touchBeginTime = 0;


		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onKeyDown(int,
		 *      android.view.KeyEvent)
		 */
		@Override
		public boolean onKeyDown(int keyCode, KeyEvent evt) {
			return keyHandler.onKeyDown(keyCode, evt);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onKeyUp(int,
		 *      android.view.KeyEvent)
		 */
		@Override
		public boolean onKeyUp(int keyCode, KeyEvent evt) {
			return keyHandler.onKeyUp(keyCode, evt);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onTouchEvent(android.view.MotionEvent)
		 */
		@Override
		public boolean onTouchEvent(final MotionEvent event) {

            vncCanvas.changeTouchCoordinatesToFitScreen(event);

            if(event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
                float[] currentPos = new float[]{event.getX(), event.getY()};


                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    touchBeginPos = currentPos;
                    lastTouchPos = currentPos;
                    touchBeginTime = System.currentTimeMillis();
                    Log.v(TAG, "Touch down at " + mousePos[0] + ":" + mousePos[1]);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (System.currentTimeMillis() - touchBeginTime < 100) {
                        event.setLocation(mousePos[0], mousePos[1]);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                event.setAction(MotionEvent.ACTION_DOWN);
                                vncCanvas.processPointerEvent(event, true, nextRight);
                                SystemClock.sleep(50);
                                event.setAction(MotionEvent.ACTION_UP);
                                vncCanvas.processPointerEvent(event, false, nextRight);
                                nextRight = false;
                            }
                        }).start();
                        Log.v(TAG, "Click at " + mousePos[0] + ":" + mousePos[1]);
                    }
                }

                float[] movement = new float[]{currentPos[0] - lastTouchPos[0], currentPos[1] - lastTouchPos[1]};

                Log.v(TAG, "Mouse movement " + movement[0] + ":" + movement[1]);
                mousePos = new float[]{mousePos[0] + movement[0], mousePos[1] + movement[1]};
                event.setLocation(mousePos[0], mousePos[1]);
                event.setAction(MotionEvent.ACTION_HOVER_MOVE);
                if (movement[0] < 400 || movement[1] < 400) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            vncCanvas.processPointerEvent(event);
                        }
                    }).start();
                } else {
                    Log.v(TAG, "Ignoring mouse movement ");
                }
                lastTouchPos = currentPos;
            }
            else if(event.getAction() == MotionEvent.ACTION_MOVE)
            {
                mousePos = new float[]{event.getX(), event.getY()};
                Log.v(TAG, "Generic mouse movement to " + mousePos[0] + ":" + mousePos[1]);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        vncCanvas.processPointerEvent(event);
                    }
                }).start();
            }
            return VncCanvasActivity.super.onTouchEvent(event);
        }

		@Override
		public boolean onGenericMotion(final MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_HOVER_MOVE) {
                vncCanvas.changeTouchCoordinatesToFitScreen(event);
                mousePos = new float[]{event.getX(), event.getY()};
                Log.v(TAG, "Generic mouse movement to " + mousePos[0] + ":" + mousePos[1]);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        vncCanvas.processPointerEvent(event);
                    }
                }).start();
            } else if (event.getAction() == MotionEvent.ACTION_BUTTON_RELEASE){
                event.setLocation(mousePos[0], mousePos[1]);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        SystemClock.sleep(50);
                        vncCanvas.processPointerEvent(event);
                    }
                }).start();
            } else if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS){
                event.setLocation(mousePos[0], mousePos[1]);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        vncCanvas.processPointerEvent(event);
                    }
                }).start();
            } else  if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER){

            } else  if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT){

            } else {
                Log.d(TAG, "Unexpected  MotionEvnet action");
            }
            return VncCanvasActivity.super.onGenericMotionEvent(event);
        }
		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onTrackballEvent(android.view.MotionEvent)
		 */
		@Override
		public boolean onTrackballEvent(MotionEvent evt) {
			return trackballMouse(evt);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#handlerDescription()
		 */
		@Override
		public CharSequence getHandlerDescription() {
			return getResources().getText(R.string.input_mode_fit_to_screen);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#getName()
		 */
		@Override
		public String getName() {
			return FIT_SCREEN_NAME;
		}

	}

	/**
	 * Touch screen controls, clicks the mouse.
	 *
	 * @author Michael A. MacDonald
	 *
	 */
	class MouseMode implements AbstractInputHandler {

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onKeyDown(int,
		 *      android.view.KeyEvent)
		 */
		@Override
		public boolean onKeyDown(int keyCode, KeyEvent evt) {
			if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
				return true;
			return defaultKeyDownHandler(keyCode, evt);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onKeyUp(int,
		 *      android.view.KeyEvent)
		 */
		@Override
		public boolean onKeyUp(int keyCode, KeyEvent evt) {
			if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
				inputHandler = getInputHandlerById(R.id.itemInputFitToScreen);
				showPanningState();
				connection.setInputMode(inputHandler.getName());
				connection.save(database.getWritableDatabase());
				updateInputMenu();
				return true;
			}
			return defaultKeyUpHandler(keyCode, evt);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onTouchEvent(android.view.MotionEvent)
		 */
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			// Mouse Pointer Control Mode
			// Pointer event is absolute coordinates.

			vncCanvas.changeTouchCoordinatesToFitScreen(event);
			if (vncCanvas.processPointerEvent(event, true))
				return true;
			return VncCanvasActivity.super.onTouchEvent(event);
		}

		@Override
		public boolean onGenericMotion(MotionEvent evt) {
			return false;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onTrackballEvent(android.view.MotionEvent)
		 */
		@Override
		public boolean onTrackballEvent(MotionEvent evt) {
			return false;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#handlerDescription()
		 */
		@Override
		public CharSequence getHandlerDescription() {
			return getResources().getText(R.string.input_mode_mouse);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#getName()
		 */
		@Override
		public String getName() {
			return "MOUSE";
		}

	}

	/**
	 * For hardware mice.
	 */
	class HardwareMouseMode extends MouseMode {
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			vncCanvas.changeTouchCoordinatesToFitScreen(event);
			if (vncCanvas.processPointerEvent(event))
				return true;
			return VncCanvasActivity.super.onTouchEvent(event);
		}

		@Override
		public boolean onGenericMotion(MotionEvent event) {
			vncCanvas.changeTouchCoordinatesToFitScreen(event);
			if (vncCanvas.processPointerEvent(event))
				return true;
			return VncCanvasActivity.super.onGenericMotionEvent(event);
		}

		@Override
		public CharSequence getHandlerDescription() {
			return getString(R.string.input_mode_hardware_mouse);
		}

		@Override
		public String getName() {
			return "HARDWARE_MOUSE";
		}
	}
}
