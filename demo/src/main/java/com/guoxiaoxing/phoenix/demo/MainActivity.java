package com.guoxiaoxing.phoenix.demo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.guoxiaoxing.phoenix.picker.ui.camera.CameraActivity;
import com.guoxiaoxing.phoenix.core.PhoenixOption;
import com.guoxiaoxing.phoenix.core.model.MediaEntity;
import com.guoxiaoxing.phoenix.core.model.MimeType;
import com.guoxiaoxing.phoenix.demo.picture.PictureDemoActivity;
import com.guoxiaoxing.phoenix.demo.video.VideoDemoActivity;
import com.guoxiaoxing.phoenix.picker.Phoenix;

import java.io.File;
import java.io.FileInputStream;
import java.text.DecimalFormat;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MediaAdapter.OnAddMediaListener
        , View.OnClickListener {

    private int REQUEST_CODE = 0x000111;
    private MediaAdapter mMediaAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getWindow() != null) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        setContentView(R.layout.activity_phoenix_demo);
        findViewById(R.id.btn_compress_picture).setOnClickListener(this);
        findViewById(R.id.btn_compress_video).setOnClickListener(this);
        findViewById(R.id.btn_take_picture).setOnClickListener(this);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(MainActivity.this, 4, GridLayoutManager.VERTICAL, false));
        mMediaAdapter = new MediaAdapter(this);
        recyclerView.setAdapter(mMediaAdapter);
        mMediaAdapter.setOnItemClickListener(new MediaAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position, View v) {
                if (mMediaAdapter.getData().size() > 0) {
                    //预览
                    Phoenix.with()
                            .pickedMediaList(mMediaAdapter.getData())
                            .start(MainActivity.this, PhoenixOption.TYPE_BROWSER_PICTURE, 0);
                }
            }
        });
    }

    @Override
    public void onaddMedia() {
        Phoenix.with()
                .theme(PhoenixOption.THEME_ORANGE)// 主题
                .fileType(MimeType.ofAll())//显示的文件类型图片、视频、图片和视频
                .maxPickNumber(10)// 最大选择数量
                .minPickNumber(1)// 最小选择数量
                .spanCount(4)// 每行显示个数
                .enablePreview(true)// 是否开启预览
                .enableCamera(false)// 是否开启拍照
                .enableAnimation(true)// 选择界面图片点击效果
                .enableCompress(true)// 是否开启压缩
                .compressPictureFilterSize(1024)//多少kb以下的图片不压缩
                .compressVideoFilterSize(2018)//多少kb以下的视频不压缩
                .thumbnailHeight(160)// 选择界面图片高度
                .thumbnailWidth(160)// 选择界面图片宽度
                .enableClickSound(false)// 是否开启点击声音
                .pickNumberMode(true)//是否开启数字显示模式
                .pickedMediaList(mMediaAdapter.getData())// 已选图片数据
                .videoFilterTime(30)//显示多少秒以内的视频
                .mediaFilterSize(0)//显示多少kb以下的图片/视频，默认为0，表示不限制
                .start(MainActivity.this, PhoenixOption.TYPE_PICK_MEDIA, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            //返回的数据
            List<MediaEntity> result = Phoenix.result(data);
            mMediaAdapter.setData(result);
            for (MediaEntity media : result) {
                Log.e("TAG", "+++++++++++++++++++++++++++++++++++++++");
                Log.e("TAG", "本地地址:" + getAutoFileOrFilesSize(media.getLocalPath()));
                Log.e("TAG", "是否原图:" + media.isCut());
                if (!TextUtils.isEmpty(media.getLocalThumbnailPath()))
                    Log.e("TAG", "本地缩略图地址:" + getAutoFileOrFilesSize(media.getLocalThumbnailPath()));
                if (!TextUtils.isEmpty(media.getOnlinePath()))
                    Log.e("TAG", "服务器地址:" + getAutoFileOrFilesSize(media.getOnlinePath()));
                if (!TextUtils.isEmpty(media.getOnlineThumbnailPath()))
                    Log.e("TAG", "服务器缩略图地址:" + media.getOnlineThumbnailPath());
                if (!TextUtils.isEmpty(media.getCompressPath()))
                    Log.e("TAG", "压缩后地址:" + getAutoFileOrFilesSize(media.getCompressPath()));
                Log.e("TAG", "+++++++++++++++++++++++++++++++++++++++");
            }
        }
    }

    /**
     * 获取指定文件大小
     *
     * @param file
     * @return
     * @throws Exception
     */
    private static long getFileSize(File file) throws Exception {
        long size = 0;
        if (file.exists()) {
            FileInputStream fis = null;
            fis = new FileInputStream(file);
            size = fis.available();
        } else {
            file.createNewFile();
            Log.e("TAG", "获取文件大小不存在!");
        }
        return size;
    }

    /**
     * 调用此方法自动计算指定文件或指定文件夹的大小
     *
     * @param filePath 文件路径
     * @return 计算好的带B、KB、MB、GB的字符串
     */

    public static String getAutoFileOrFilesSize(String filePath) {
        File file = new File(filePath);
        long blockSize = 0;
        try {
            blockSize = getFileSize(file);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("TAG", "获取文件大小失败!");
        }
        return FormetFileSize(blockSize);
    }

    /**
     * 转换文件大小
     *
     * @param fileS
     * @return
     */
    private static String FormetFileSize(long fileS) {
        DecimalFormat df = new DecimalFormat("#.00");
        String fileSizeString = "";
        String wrongSize = "0B";
        if (fileS == 0) {
            return wrongSize;
        }
        if (fileS < 1024) {
            fileSizeString = df.format((double) fileS) + "B";
        } else if (fileS < 1048576) {
            fileSizeString = df.format((double) fileS / 1024) + "KB";
        } else if (fileS < 1073741824) {
            fileSizeString = df.format((double) fileS / 1048576) + "MB";
        } else {
            fileSizeString = df.format((double) fileS / 1073741824) + "GB";
        }
        return fileSizeString;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_compress_picture:
                startActivity(new Intent(MainActivity.this, PictureDemoActivity.class));
                break;
            case R.id.btn_compress_video:
                startActivity(new Intent(MainActivity.this, VideoDemoActivity.class));
                break;
            case R.id.btn_take_picture:
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
                break;
        }
    }
}
