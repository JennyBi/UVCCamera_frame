package com.goertek.uvc_camera_unity_plugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.IStatusCallback;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;


public class UVCCameraActivity extends UnityPlayerActivity
{
    protected  static UVCCameraActivity _instance = null;
    public static UVCCameraActivity GetInstance()
    {
        return _instance;
    }

    protected SurfaceTexture mRenderTexture ;
    protected  int mPreviewPixelFormat = UVCCamera.PIXEL_FORMAT_RAW;
    protected  int mPreviewWidth = UVCCamera.DEFAULT_PREVIEW_WIDTH;
    protected  int mPreviewHeight = UVCCamera.DEFAULT_PREVIEW_HEIGHT;
    protected  int mTextureID = -1;
    protected UVCCamera mUVCCamera = null;
    protected USBMonitor mUSBMonitor = null;
    protected OnDeviceConnectListener mOnDeviceConnectListener;
    protected Boolean mConnected = false;
    protected Boolean mIsPreviewing = false;
    protected Boolean mIsCapturing = false;
    protected byte[] mbytes;
    protected Context context;

    void ProcessBuffer (byte[] buffer, int length){};

    public  UVCCameraActivity()
    {
        Log("Constructor called");
        _instance = this;
    }
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {"android.permission.READ_EXTERNAL_STORAGE","android.permission.WRITE_EXTERNAL_STORAGE" };


    public static void verifyStoragePermissions(Activity activity) {

        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void QueryDevices()
    {
        Log("QueryDevices");

        if( mUSBMonitor != null )
        {
            List<UsbDevice> devices = mUSBMonitor.getDeviceList();

            Log(devices.size() + " usb devices found!");
            for( UsbDevice device : devices )
            {
                Log("Device found = " + device.getDeviceName());
            }
        }
    }

    public  void ChangePreviewFormat(int pixelFormat)
    {
        mPreviewPixelFormat = pixelFormat;

        if( mUVCCamera != null )
        {
            mUVCCamera.setPreviewSize(mPreviewWidth, mPreviewHeight, mPreviewPixelFormat);
        }
    }

    public void StartPreview(int width, int height, int pixelformat)
    {
        Log("StartPreview");

        if( mUVCCamera != null && mConnected )
        {
            //
            mPreviewWidth = width;//width;
            mPreviewHeight = height;//height;
            mPreviewPixelFormat = pixelformat;

            Log("supportedSize:" + mUVCCamera.getSupportedSize());
            Toast.makeText(context,"supportedSize:" + mUVCCamera.getSupportedSize(),Toast.LENGTH_SHORT).show();
            //
            try
            {
                mUVCCamera.setPreviewSize(mPreviewWidth, mPreviewHeight, mPreviewPixelFormat);
                Log.d("Starting preview  : ","mPreviewWidth: " + mPreviewWidth + "mPreviewHeight:" + mPreviewHeight + " format : " + mPreviewPixelFormat);
            } catch (final IllegalArgumentException e)
            {
                // fallback to YUV mode
                try
                {
                    Log("Fallback to YUV mode");
                    Toast.makeText(context,e.toString(),Toast.LENGTH_SHORT).show();

                    mUVCCamera.setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG);
                } catch (final IllegalArgumentException e1)
                {
                    Toast.makeText(context,e1.toString(),Toast.LENGTH_SHORT).show();
                    Log(e1.toString());
                    mUVCCamera.destroy();
                    mUVCCamera = null;

                    mTextureID = -1;
                    mConnected = false;
                    mIsPreviewing = false;
                }
            }
            //Toast.makeText(context,"1111111111111",Toast.LENGTH_SHORT).show();
            if( mUVCCamera != null )
            {
               // Toast.makeText(context,"mUVCCamera != null",Toast.LENGTH_SHORT).show();
                //
                if (mTextureID == -1) {
                    int textures[] = new int[1];
                    GLES20.glGenTextures(1, textures, 0);
                    mTextureID = textures[0];
                    mRenderTexture = new SurfaceTexture(mTextureID);
                    Log("New Texture ID000 : " + mTextureID);
                }
                Log("New Texture ID111 : " + mTextureID);
                mConnected = true;
                mUVCCamera.setPreviewTexture(mRenderTexture);


                mUVCCamera.setFrameCallback(new IFrameCallback() {
                    @Override
                    public void onFrame(ByteBuffer frame) throws IOException {
                        // this is where the magic happens
                        Log("onframe: xxx");

//                        Bitmap bitmap=Yuv2Jpeg(decodeValue(frame));
//                        saveImageToGallery(context, bitmap);

                        byte[] bytes=mdecodeValue(frame);
                        ProcessBuffer(bytes,bytes.length);
                        //UnityPlayer.UnitySendMessage(objectName, methodName, message);

                    }
                }, mPreviewPixelFormat);
                Log("mUVCCamera.startPreviewing... " );
                mUVCCamera.startPreview();
                mIsPreviewing = true;

            }
        }
    }


    private byte[] decodeValue(ByteBuffer bytes) {
        Log("frame:bytes "+bytes);
        int len = bytes.limit() - bytes.position();
        byte[] bytes1 = new byte[len];
        bytes.get(bytes1);
        return bytes1;
    }

    private Bitmap Yuv2Jpeg(byte[] data) {
        Log("data.size: "+data.length);
        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, mUVCCamera.DEFAULT_PREVIEW_WIDTH, mUVCCamera.DEFAULT_PREVIEW_HEIGHT, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        boolean result = yuvImage.compressToJpeg(new Rect(0, 0, mUVCCamera.DEFAULT_PREVIEW_WIDTH, mUVCCamera.DEFAULT_PREVIEW_HEIGHT), 100, bos);
        //if (result) {
        byte[] buffer = bos.toByteArray();
        Bitmap bmp = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
        return bmp;
        //}
    }

    private void saveImageToGallery(Context context, Bitmap bmp) {
        // 首先保存图片
        File appDir = new File(Environment.getExternalStorageDirectory(), "uvc123");
        Log( "path: "+Environment.getExternalStorageDirectory());

        if (!appDir.exists()) {
            Log("mkdir...");
            appDir.mkdirs();
        }

        String fileName = System.currentTimeMillis() + ".jpg";
        File file = new File(appDir, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 其次把文件插入到系统图库
        try {
            MediaStore.Images.Media.insertImage(context.getContentResolver(),
                    file.getAbsolutePath(), fileName, null);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        // 最后通知图库更新
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse(file.getAbsolutePath())));

    }

    private byte[] mdecodeValue(ByteBuffer bytes) {
        Log("save:bytes "+bytes);
        int len = bytes.limit() - bytes.position();
        mbytes = new byte[len];
        bytes.get(mbytes);
        return mbytes;
    }
    private final byte[] copy(byte[] source) {
        Class type = source.getClass().getComponentType();
        byte[] target = (byte[]) Array.newInstance(type, source.length+1);
        System.arraycopy(source, 0, target, 0, source.length);
        return target;
    }

    public byte[] CapturePic()
    {
        Toast.makeText(context,"CapturePicture ",Toast.LENGTH_SHORT).show();

        Log("CapturePic");
        mIsCapturing=true;
        if( mUVCCamera != null && mConnected )
        {
            //
            mPreviewWidth = 640;//width;
            mPreviewHeight = 480;//height;
            mPreviewPixelFormat = 4;

            Log("supportedSize:" + mUVCCamera.getSupportedSize());

            mUVCCamera.setPreviewSize(mPreviewWidth, mPreviewHeight, mPreviewPixelFormat);

            if( mUVCCamera != null )
            {
                // Toast.makeText(context,"mUVCCamera != null",Toast.LENGTH_SHORT).show();
                //
                if (mTextureID == -1) {
                    int textures[] = new int[1];
                    GLES20.glGenTextures(1, textures, 0);
                    mTextureID = textures[0];
                    mRenderTexture = new SurfaceTexture(mTextureID);
                    Log("New Texture ID000 : " + mTextureID);
                }
                Log("New Texture ID111 : " + mTextureID);
                mConnected = true;
                mUVCCamera.setPreviewTexture(mRenderTexture);

                mUVCCamera.setFrameCallback(new IFrameCallback() {
                    @Override
                    public void onFrame(ByteBuffer frame) throws IOException {
                        // this is where the magic happens
                        Log("onframe: 000");

                       // System.arraycopy (mdecodeValue(frame),0,mbytes,0,decodeValue(frame).length);

                        if(mIsCapturing) {
                            mbytes=copy(mdecodeValue(frame));
                            Log("copy byte to mbytes");
                            Log("save bitmap ");
                            Bitmap bitmap = Yuv2Jpeg(mbytes);
                            saveImageToGallery(context, bitmap);
                            mIsCapturing=false;
                        }

                    }
                }, mPreviewPixelFormat);

                Log("picture saved!" );

                mUVCCamera.startPreview();
                mIsPreviewing = true;
                Toast.makeText(context,"data.length: "+String.valueOf(mbytes.length),Toast.LENGTH_SHORT).show();
            }
        }

        return mbytes;
    }


    public void Disconnect()
    {
        if( mConnected )
        {
            if( mUVCCamera != null )
            {
                mUVCCamera.destroy();
                mUVCCamera = null;

                //  mRenderTexture.release();
                //  mRenderTexture = null;

                //  mTextureID = -1;
                mConnected = false;
                mIsPreviewing = false;
            }
        }
    }

    public int GetIsPreviewing()
    {
        if( mIsPreviewing )
            return 1;

        return 0;
    }

    public int GetIsConnected()
    {
        if( mConnected )
            return 1;

        return 0;
    }

    public String[] GetSupportedResolutions()
    {
        if( mConnected )
        {
            List<Size> sizes = mUVCCamera.getSupportedSizeList();
            String[] sizesString = new String[sizes.size()];

            int i = 0;
            for(Size size : sizes)
            {
                //     Log("Supported size : " + size.toString() + " found!");
                sizesString[i] = size.width + "x" + size.height;

                i++;
            }

            return sizesString;
        }

        return  null;
    }

    public String[] GetDeviceList()
    {
        Log("GetDeviceList ");

        if( mUSBMonitor != null )
        {
            List<UsbDevice> devices = mUSBMonitor.getDeviceList();
            List<UsbDevice> cameras = new ArrayList<UsbDevice>();

            for(UsbDevice device : devices )
            {
                for( int i = 0; i < device.getInterfaceCount(); i++ )
                {
                    UsbInterface iface = device.getInterface(i);

                    Log("UsbInterface device [" + device.getDeviceName() + "] found with interface [" + iface.getInterfaceClass() + " !" );

                    if(         iface.getInterfaceClass() == UsbConstants.USB_CLASS_VIDEO
                            ||  iface.getInterfaceClass() == UsbConstants.USB_CLASS_AUDIO
                            ||  iface.getInterfaceClass() == UsbConstants.USB_CLASS_MISC)
                    {
                        cameras.add(device);
                        break;
                    }
                }

            }

            String[] cameraList = new String[cameras.size()];

            int i = 0;
            for(UsbDevice camera : cameras )
            {
                cameraList[i] = camera.getDeviceName();
                i++;
            }

            return cameraList;
        }

        return null;
    }

    public void Connect(String deviceName)
    {
        //
        Log("Connect " + deviceName  + " " + mPreviewWidth + "x" + mPreviewHeight + " : " + mPreviewPixelFormat );

        if( mConnected )
        {
            Disconnect();
        }

        if( !mConnected )
        {
            if( mUSBMonitor != null )
            {
                List<UsbDevice> devices = mUSBMonitor.getDeviceList();

                for(int i = 0; i < devices.size() ; i++ )
                {
                    UsbDevice device = devices.get(i);
                    // Log("Checking " + deviceName + " against " + device.getDeviceName() );
                    if(device.getDeviceName().contains(deviceName))
                    {
                        Log("Request permission of device " + deviceName);
                        mUSBMonitor.requestPermission(device);

                        return;
                    }
                }
            }
        }
    }

    public int GetFocus()
    {
        Log("GetFocus called!");

        if( mUVCCamera != null )
        {
            return mUVCCamera.getFocus();
        }

        return  -1;
    }

    public  void SetFocus(int value)
    {
        Log("SetFocus " + value);

        if( mUVCCamera != null )
        {
            mUVCCamera.setFocus(value);
        }
    }

    protected void onCreate(Bundle savedInstanceState)
    {
        //
        Log("onCreate called!");

        // call UnityPlayerActivity.onCreate()
        super.onCreate(savedInstanceState);
        context=getApplicationContext();

        Toast.makeText(context,"onCreate",Toast.LENGTH_SHORT).show();
        mUSBMonitor = new USBMonitor(this, mDeviceConnectListener);
        Toast.makeText(context,"new USBMonitor",Toast.LENGTH_SHORT).show();
        mUSBMonitor.register();
        Toast.makeText(context,"mUSBMonitor.register()",Toast.LENGTH_SHORT).show();
    }

    public void Log(String log)
    {
        Log.d("Unity : ", "UVCCameraInterface -> " + log);
    }

    //
    protected OnDeviceConnectListener mDeviceConnectListener = new OnDeviceConnectListener()
    {
        @Override
        public void onAttach(final UsbDevice device)
        {
            Log("onAttach");

            if( device != null )
            {
                Log( "USB_DEVICE_ATTACHED " +device.getDeviceName());
            }
        }


        @Override
        public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew)
        {
            Log("onConnect");

            if( device != null )
            {
                if (mUVCCamera != null)
                    mUVCCamera.destroy();

                mUVCCamera = new UVCCamera();

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run() {
                        mUVCCamera.open(ctrlBlock);
                        mUVCCamera.setStatusCallback(new IStatusCallback() {
                            @Override
                            public void onStatus(final int statusClass, final int event, final int selector,
                                                 final int statusAttribute, final ByteBuffer data) {
                                new Runnable() {
                                    @Override
                                    public void run() {
                                    }
                                };
                            }
                        });
                            if (mRenderTexture != null) {
                                //    mRenderTexture.release();
                                //   mRenderTexture = null;
                            }

                            Log("Connected!");
                            mConnected = true;
                        }
                });
            }
        }


        @Override
        public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock)
        {
            Log("onDisconnect");

            if( device != null )
            {
                // XXX you should check whether the coming device equal to camera device that currently using
                if (mUVCCamera != null)
                {
                    mUVCCamera.close();
                    if (mRenderTexture  != null)
                    {
                        //    mRenderTexture .release();
                        //    mRenderTexture  = null;
                    }

                    //        mTextureID = -1;
                    mConnected = false;
                    mIsPreviewing = false;
                }
            }

        }

        @Override
        public void onDettach(final UsbDevice device)
        {
            Log("onDettach");

            if( device != null )
            {
                Log( "USB_DEVICE_DETACHED : " + device.getDeviceName());
            }
        }

        @Override
        public void onCancel()
        {
            Log("onCancel");
        }
    };

    public  void SetExposure(int value)
    {
        if( mUVCCamera != null )
        {
            mUVCCamera.setExposure(value);
        }
    }

    public  int GetExposure()
    {
        if( mUVCCamera != null )
        {
            return  mUVCCamera.getExposure();
        }

        return 0;
    }

    public int GetExposureMode()
    {
        if( mUVCCamera != null )
        {
            return  mUVCCamera.getExposureMode();
        }

        return 0;
    }

    public void SetExposureMode(int value)
    {
        if( mUVCCamera != null )
        {
            mUVCCamera.setExposureMode(value);
        }
    }

    public  void SetGamma(int value)
    {
        if( mUVCCamera != null )
        {
            mUVCCamera.setGamma(value);
        }
    }

    public  int GetGamma()
    {
        if( mUVCCamera != null )
        {
            return  mUVCCamera.getGamma();
        }

        return 0;
    }
}
