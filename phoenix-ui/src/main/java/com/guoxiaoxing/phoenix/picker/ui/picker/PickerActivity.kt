package com.guoxiaoxing.phoenix.picker.ui.picker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.SimpleItemAnimator
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.guoxiaoxing.phoenix.R
import com.guoxiaoxing.phoenix.core.PhoenixOption.THEME_DEFAULT
import com.guoxiaoxing.phoenix.core.common.PhoenixConstant
import com.guoxiaoxing.phoenix.core.model.MediaEntity
import com.guoxiaoxing.phoenix.core.model.MimeType
import com.guoxiaoxing.phoenix.picker.adapter.PickerAdapter
import com.guoxiaoxing.phoenix.picker.adapter.PickerAlbumAdapter
import com.guoxiaoxing.phoenix.picker.model.EventEntity
import com.guoxiaoxing.phoenix.picker.model.MediaFolder
import com.guoxiaoxing.phoenix.picker.model.MediaLoader
import com.guoxiaoxing.phoenix.picker.rx.bus.ImagesObservable
import com.guoxiaoxing.phoenix.picker.rx.bus.RxBus
import com.guoxiaoxing.phoenix.picker.rx.bus.Subscribe
import com.guoxiaoxing.phoenix.picker.rx.bus.ThreadMode
import com.guoxiaoxing.phoenix.picker.rx.permission.RxPermissions
import com.guoxiaoxing.phoenix.picker.ui.BaseActivity
import com.guoxiaoxing.phoenix.picker.ui.Navigator
import com.guoxiaoxing.phoenix.picker.ui.camera.CameraActivity
import com.guoxiaoxing.phoenix.picker.util.*
import com.guoxiaoxing.phoenix.picker.widget.FolderPopWindow
import com.guoxiaoxing.phoenix.picker.widget.GridSpacingItemDecoration
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_picker.*
import kotlinx.android.synthetic.main.include_title_bar.*
import java.io.File
import java.io.FileInputStream
import java.text.DecimalFormat
import java.util.*

/**
 * For more information, you can visit https://github.com/guoxiaoxing or contact me by
 * guoxiaoxingse@163.com.
 *
 * @author guoxiaoxing
 * @since 2017/10/19 ??????6:30
 */
class PickerActivity : BaseActivity(), View.OnClickListener, PickerAlbumAdapter.OnItemClickListener,
        PickerAdapter.OnPickChangedListener {

    private val TAG = PickerActivity::class.java.simpleName

    private lateinit var pickAdapter: PickerAdapter
    private var allMediaList: MutableList<MediaEntity> = ArrayList()
    private var allFolderList: MutableList<MediaFolder> = ArrayList()

    private var isAnimation = false
    private lateinit var folderWindow: FolderPopWindow
    private var animation: Animation? = null
    private lateinit var rxPermissions: RxPermissions
    private lateinit var mediaLoader: MediaLoader
    private var selectImages: List<MediaEntity> = ArrayList()

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun eventBus(obj: EventEntity) {
        when (obj.what) {
            //receive the select result from CameraPreviewActivity
            PhoenixConstant.FLAG_PREVIEW_UPDATE_SELECT -> {
                val selectImages = obj.mediaEntities
                isAnimation = selectImages.size > 0
                val position = obj.position
                DebugUtil.i(TAG, "????????????::" + position)
                pickAdapter.setPickMediaList(selectImages)
                //??????????????????????????????
                val isExceedMax = selectImages.size >= maxSelectNum && maxSelectNum != 0
                pickAdapter.isExceedMax = isExceedMax
                if (isExceedMax || selectImages.size == maxSelectNum - 1) {
                    pickAdapter.notifyDataSetChanged()
                } else {
                    pickAdapter.notifyItemChanged(position)
                }
            }
            PhoenixConstant.FLAG_PREVIEW_COMPLETE -> {
                val mediaEntities = obj.mediaEntities
                processMedia(mediaEntities)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ToolbarUtil.setColorNoTranslucent(this, themeColor)
        LightStatusBarUtils.setLightStatusBar(this, false)
        if (!RxBus.default.isRegistered(this)) {
            RxBus.default.register(this)
        }
        rxPermissions = RxPermissions(this)

        rxPermissions.request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe(object : Observer<Boolean> {
                    override fun onSubscribe(d: Disposable) {}

                    override fun onNext(aBoolean: Boolean) {
                        if (aBoolean) {
                            setContentView(R.layout.activity_picker)
                            setupView()
                            setupData()
                        } else {
                            showToast(getString(R.string.picture_jurisdiction))
                            closeActivity()
                        }
                    }

                    override fun onError(e: Throwable) {
                        showToast(getString(R.string.picture_jurisdiction))
                        closeActivity()
                    }

                    override fun onComplete() {}
                })
    }

    /**
     * init views
     */
    private fun setupView() {
        pickRlTitle.setBackgroundColor(themeColor)

        if (themeColor == THEME_DEFAULT) {
            rl_bottom.setBackgroundColor(themeColor)

        } else {
            rl_bottom.setBackgroundColor(Color.WHITE)
            pickTvPreview.setTextColor(themeColor)
            pickLlOk.background = tintDrawable(R.drawable.phoenix_shape_complete_background, themeColor)
        }

        isNumberComplete()
        pickTvTitle.text = if (fileType == MimeType.ofAudio()) getString(R.string.picture_all_audio) else getString(R.string.picture_camera_roll)
        pick_tv_empty.text = if (fileType == MimeType.ofAudio()) getString(R.string.picture_audio_empty) else getString(R.string.picture_empty)
        StringUtils.tempTextFont(pick_tv_empty, fileType)

        val titleText = pickTvTitle.getText().toString().trim { it <= ' ' }
        if (enableCamera) {
            enableCamera = StringUtils.isCamera(titleText)
        }

        folderWindow = FolderPopWindow(this, fileType)
        folderWindow.setPictureTitleView(pickTvTitle)
        folderWindow.setOnItemClickListener(this)

        pickTvPreview.setOnClickListener(this)
        pickTvBack.setOnClickListener(this)
        pickTvCancel.setOnClickListener(this)
        pickLlOk.setOnClickListener(this)
        pickTvTitle.setOnClickListener(this)
        original_picture.setOnClickListener(this)
    }

    private fun setupData() {
        pickRecyclerView.setHasFixedSize(true)
        pickRecyclerView.addItemDecoration(GridSpacingItemDecoration(spanCount,
                ScreenUtil.dip2px(this, 2f), false))
        pickRecyclerView.layoutManager = GridLayoutManager(this, spanCount)
        (pickRecyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        pickAdapter = PickerAdapter(mContext, option)
        pickRecyclerView.adapter = pickAdapter
        pickAdapter.setOnPickChangedListener(this)
        pickAdapter.setPickMediaList(mediaList)
        changeImageNumber(mediaList)

        mediaLoader = MediaLoader(this, fileType, isGif, videoFilterTime.toLong(), mediaFilterSize)
        rxPermissions.request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe(object : Observer<Boolean> {
                    override fun onSubscribe(d: Disposable) {}

                    override fun onNext(aBoolean: Boolean) {
                        showLoadingDialog()
                        if (aBoolean) {
                            readLocalMedia()
                        } else {
                            showToast(getString(R.string.picture_jurisdiction))
                            dismissLoadingDialog()
                        }
                    }

                    override fun onError(e: Throwable) {}

                    override fun onComplete() {}
                })
    }

    /**
     * none number style
     */
    @SuppressLint("StringFormatMatches")
    private fun isNumberComplete() {
        pickTvOk.text = getString(R.string.picture_please_select)
        animation = AnimationUtils.loadAnimation(this, R.anim.phoenix_window_in)
    }

    /**
     * get MediaEntity s
     */
    private fun readLocalMedia() {
        mediaLoader.loadAllMedia(object : MediaLoader.LocalMediaLoadListener {
            override fun loadComplete(folders: MutableList<MediaFolder>) {
                if (folders.size > 0) {
                    allFolderList = folders
                    val folder = folders[0]
                    folder.isChecked = true
                    val localImg = folder.images
                    // ??????????????????????????????????????????????????????????????????????????????
                    // ??????onActivityResult????????????????????????????????????
                    // ????????????????????????????????????????????????adapter?????????????????????????????????????????????????????????
                    if (localImg.size >= allMediaList.size) {
                        allMediaList = localImg
                        folderWindow.bindFolder(folders)
                    }
                }
                if (pickAdapter != null) {
                    if (allMediaList == null) {
                        allMediaList = ArrayList()
                    }
                    pickAdapter.setAllMediaList(allMediaList)
                    pick_tv_empty.visibility = if (allMediaList.size > 0) View.INVISIBLE else View.VISIBLE
                }
                dismissLoadingDialog()
            }
        })
    }

    @SuppressLint("StringFormatMatches")
    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.pickTvBack || id == R.id.pickTvCancel) {
            if (folderWindow.isShowing) {
                folderWindow.dismiss()
            } else {
                closeActivity()
            }
        }
        if (id == R.id.pickTvTitle) {
            if (folderWindow.isShowing()) {
                folderWindow.dismiss()
            } else {
                if (allMediaList.size > 0) {
                    folderWindow.showAsDropDown(pickRlTitle)
                    val selectedImages = pickAdapter.getPickMediaList()
                    folderWindow.notifyDataCheckedStatus(selectedImages)
                }
            }
        }

        if (id == R.id.pickTvPreview) {
            val pickedImages = pickAdapter.getPickMediaList()
            Navigator.showPreviewView(this, option, pickedImages, pickedImages, 0)
        }

        if (id == R.id.pickLlOk) {
            val images = pickAdapter.getPickMediaList()
            val pictureType = if (images.size > 0) images[0].mimeType else ""
            val size = images.size
            val eqImg = !TextUtils.isEmpty(pictureType) && pictureType.startsWith(PhoenixConstant.IMAGE)

            // ?????????????????????????????????????????????????????????????????????
            if (minSelectNum > 0) {
                if (size < minSelectNum) {
                    @SuppressLint("StringFormatMatches") val str = if (eqImg)
                        getString(R.string.picture_min_img_num, minSelectNum)
                    else
                        getString(R.string.phoenix_message_min_number, minSelectNum)
                    showToast(str)
                    return
                }
            }
            processMedia(images)

        }
        if (id == R.id.original_picture) {
            FilesSize()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //processMedia(allMediaList)   //??????????????????????????????

        if (Activity.RESULT_OK != resultCode) return

//        val result = data!!.getSerializableExtra(PhoenixConstant.PHOENIX_RESULT) as MutableList<MediaEntity>
//        DebugUtil.i("PickerActivity:OnResult: ", result.size.toString())
//        DebugUtil.i("PickerActivity:OnResult: ", option.maxPickNumber.toString())

        when (requestCode) {
            PhoenixConstant.REQUEST_CODE_CAPTURE -> {
                onResult(data!!.getSerializableExtra(PhoenixConstant.PHOENIX_RESULT) as MutableList<MediaEntity>)
            }
            else -> {
            }
        }
    }

    override fun onItemClick(folderName: String, images: MutableList<MediaEntity>) {
        pickTvTitle.text = folderName
        pickAdapter.setAllMediaList(images)
        folderWindow.dismiss()
    }

    override fun onTakePhoto() {
        // ??????????????????,????????????????????????????????????
        rxPermissions.request(Manifest.permission.CAMERA).subscribe(object : Observer<Boolean> {
            override fun onSubscribe(d: Disposable) {

            }

            override fun onNext(aBoolean: Boolean) {
                if (aBoolean) {
                    startCamera()
                } else {
                    showToast(getString(R.string.picture_camera))
                    if (enableCamera) {
                        closeActivity()
                    }
                }
            }

            override fun onError(e: Throwable) {

            }

            override fun onComplete() {

            }
        })
    }

    override fun onChange(selectImages: List<MediaEntity>) {
        changeImageNumber(selectImages)
        this.selectImages = selectImages
        FilesSize()

    }

    fun FilesSize() {
        var FilesSize = 0.0
        if (selectImages.size > 0)
            if (original_picture.isChecked) {
                pickAdapter.setPickMediaList(true)
                for (media in selectImages) {
                    Log.e("TAG", "????????????:" + getAutoFileOrFilesSize(media.localPath))
                    FilesSize += getAutoFileOrFilesSize(media.localPath)!!.toDouble()
                }
                val FilesSizel = FilesSize.toLong()
                val df = DecimalFormat("#.00")
                var FilesSizes = if (FilesSizel < 1024) {
                    df.format(FilesSizel.toDouble()) + "B"
                } else if (FilesSizel < 1048576) {
                    df.format(FilesSizel.toDouble() / 1024) + "KB"
                } else if (FilesSizel < 1073741824) {
                    df.format(FilesSizel.toDouble() / 1048576) + "MB"
                } else {
                    df.format(FilesSizel.toDouble() / 1073741824) + "GB"
                }
                Log.e("TAG", "????????????:" + FilesSizes)
                original_picture.text = "?????? (" + FilesSizes + ")"
            } else {
                pickAdapter.setPickMediaList(false)
                original_picture.text = "??????"
            }
        else
            original_picture.text = "??????"
    }

    override fun onPictureClick(mediaEntity: MediaEntity, position: Int) {
        Navigator.showPreviewView(this, option, pickAdapter.getAllMediaList(), pickAdapter.getPickMediaList(), position)
    }

    /**
     * change image selector state

     * @param selectImages
     */
    @SuppressLint("StringFormatMatches")
    private fun changeImageNumber(selectImages: List<MediaEntity>) {
        val enable = selectImages.isNotEmpty()
        if (enable) {
            pickLlOk.isEnabled = true
            pickLlOk.alpha = 1F
            pickTvPreview.isEnabled = true
            pickTvPreview.setTextColor(if (themeColor == THEME_DEFAULT) ContextCompat.getColor(mContext, R.color.green) else themeColor)
            if (!isAnimation) {
                pickTvNumber.startAnimation(animation)
            }
            pickTvNumber.visibility = View.VISIBLE
            pickTvNumber.text = String.format("(%d)", selectImages.size)
            pickTvOk.text = getString(R.string.picture_completed)
            isAnimation = false
        } else {
            pickLlOk.isEnabled = false
            pickLlOk.alpha = 0.7F
            pickTvPreview.isEnabled = false
            pickTvPreview.setTextColor(ContextCompat.getColor(mContext, R.color.color_gray_1))
            pickTvNumber.visibility = View.GONE
            pickTvOk.text = getString(R.string.picture_please_select)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        closeActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (RxBus.default.isRegistered(this)) {
            RxBus.default.unregister(this)
        }
        ImagesObservable.instance.clearCachedData()
        animation?.cancel()
    }

    private fun startCamera() {
        val bundle = Bundle()
        bundle.putParcelable(PhoenixConstant.PHOENIX_OPTION, option)
        startActivity(CameraActivity::class.java, bundle, PhoenixConstant.REQUEST_CODE_CAPTURE)
        overridePendingTransition(R.anim.phoenix_activity_in, 0)
    }

    /**
     * ????????????????????????
     *
     * @param file
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun getFileSize(file: File): Long {
        var size: Long = 0
        if (file.exists()) {
            var fis: FileInputStream? = null
            fis = FileInputStream(file)
            size = fis.available().toLong()
        } else {
            file.createNewFile()
            Log.e("TAG", "???????????????????????????!")
        }
        return size
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     *
     * @param filePath ????????????
     * @return ???????????????B???KB???MB???GB????????????
     */
    fun getAutoFileOrFilesSize(filePath: String?): String? {
        val file = File(filePath)
        var blockSize: Long = 0
        try {
            blockSize = getFileSize(file)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("TAG", "????????????????????????!")
        }
        return FormetFileSize(blockSize)
    }

    /**
     * ??????????????????
     *
     * @param fileS
     * @return
     */
    private fun FormetFileSize(fileS: Long): String? {
        val df = DecimalFormat("#.00")
        var fileSizeString = ""
        val wrongSize = "0B"
        if (fileS == 0L) {
            return wrongSize
        }
//        fileSizeString = if (fileS < 1024) {
//            df.format(fileS.toDouble()) //+ "B"
//        } else if (fileS < 1048576) {
        fileSizeString = df.format(fileS.toDouble() / 1024) //+ "KB"
//        } else if (fileS < 1073741824) {
//            df.format(fileS.toDouble() / 1048576) //+ "MB"
//        } else {
//            df.format(fileS.toDouble() / 1073741824) //+ "GB"
//        }
        return fileSizeString
    }
}