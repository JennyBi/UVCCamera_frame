在回调函数IFrameCallback中拍照和保存实时画面帧。


                    mUVCCamera.setFrameCallback(new IFrameCallback() {
                    @Override
                    public void onFrame(ByteBuffer frame) throws IOException {
                        if(mIsCapturing) {
                            mbytes=copy(mdecodeValue(frame));
                            Log("copy byte to mbytes");
                            Log("save bitmap ");
                            Bitmap bitmap = Yuv2Jpeg(mbytes);
                            saveImageToGallery(context, bitmap);
                            mIsCapturing=false;
                        }}
                }, mPreviewPixelFormat);


Andorid Studio:3.4.1

Andorid SDK Tools:26.1.1

API29

ndk

androidlibrary
