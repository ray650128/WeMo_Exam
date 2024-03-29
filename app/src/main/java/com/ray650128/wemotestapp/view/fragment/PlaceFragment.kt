package com.ray650128.wemotestapp.view.fragment

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.ray650128.wemotestapp.R
import com.ray650128.wemotestapp.databinding.FragmentPlaceBinding
import com.ray650128.wemotestapp.databinding.FragmentPlaceEditModeBinding
import com.ray650128.wemotestapp.model.Place
import com.ray650128.wemotestapp.util.LocationUtil
import com.ray650128.wemotestapp.view.adapter.PlacePhotoAdapter
import com.ray650128.wemotestapp.viewModel.PlaceListViewModel
import io.realm.RealmList
import java.io.File
import java.text.SimpleDateFormat


class PlaceFragment : Fragment(), OnMapReadyCallback {

    private val args: PlaceFragmentArgs by navArgs()

    private var editMode = 0

    private var dataId = -1

    private var binding: ViewBinding? = null

    private val viewModel: PlaceListViewModel by activityViewModels()

    private var photoList: ArrayList<String> = arrayListOf()

    private var photoListAdapter: PlacePhotoAdapter = PlacePhotoAdapter()

    private var photoPath: String = ""
    private var currentPhotoIndex: Int = -1

    private lateinit var mMap: GoogleMap

    private lateinit var marker: Marker
    private var markerPosition = LatLng(0.0, 0.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        editMode = args.editMode
        dataId = args.dataId

        viewModel.getData(dataId)

        viewModel.isPlaceDetailShow.postValue(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.isPlaceDetailShow.postValue(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = when (editMode) {
            VIEW_MODE -> FragmentPlaceBinding.inflate(inflater, container, false)
            else -> FragmentPlaceEditModeBinding.inflate(inflater, container, false)
        }

        // Inflate the layout for this fragment
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this@PlaceFragment)

        viewModel.placeData.observe(viewLifecycleOwner) {
            when (editMode) {
                VIEW_MODE -> viewModeCase(it)
                EDIT_MODE -> editModeCase(it)
                else -> addModeCase()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == PICTURE_FROM_GALLERY) {
            if (data != null && data.data != null) {
                photoPath = getPathFromUri(data.data!!)
                if (photoList.size < 3) {
                    photoList.add(photoPath)
                } else {
                    photoList[currentPhotoIndex] = photoPath
                }
                photoListAdapter.updateData(photoList, true)
            }
        }

        if (resultCode == RESULT_OK && requestCode == PICTURE_FROM_CAMERA) {
            if (photoList.size < 3) {
                photoList.add(photoPath)
            } else {
                photoList[currentPhotoIndex] = photoPath
            }
            photoListAdapter.updateData(photoList, true)
        }
    }

    /**
     * 顯示照片來源選單
     */
    private fun showPhotoPickerMenu() {
        val popupMenu = AlertDialog.Builder(requireContext()).apply {
            setTitle(getString(R.string.text_photo_picker_title))
            setCancelable(true)
            setItems(R.array.gallery_menu) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> callGallery()
                    1 -> callCamera()
                }
            }
        }
        popupMenu.show()
    }

    /**
     * 呼叫外部相簿取得照片
     */
    private fun callGallery() {
        val gallery = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.INTERNAL_CONTENT_URI)
        startActivityForResult(gallery, PICTURE_FROM_GALLERY)
    }

    /**
     * 呼叫相機取得照片
     */
    @SuppressLint("SimpleDateFormat")
    private fun callCamera() {
        val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val date = SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis())
        //先新增一張照片
        val tmpFile = File(requireContext().getExternalFilesDir(null), "image_$date.jpg")

        //建立uri，這邊拿到的格式就會是 content://了
        val outputFileUri = FileProvider.getUriForFile(
            requireActivity(),
            "com.ray650128.wemotestapp.provider",
            tmpFile
        )

        photoPath = tmpFile.absolutePath

        //指定為輸出檔案的位置
        camera.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri)
        startActivityForResult(camera, PICTURE_FROM_CAMERA)
    }

    /**
     * 取得檔案路徑
     * @param uri   來源URI
     * @return  檔案路徑
     */
    private fun getPathFromUri(uri: Uri): String {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        val cursor = requireActivity().contentResolver.query(uri, projection, null, null, null)
        val columnIndex: Int? = cursor?.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
        cursor?.moveToFirst()
        val result: String = columnIndex?.let { cursor.getString(it) } ?: ""
        cursor?.close()
        return result
    }

    /**
     * 顯示明細
     * @param data  傳入的資料
     */
    private fun viewModeCase(data: Place?) = (binding as FragmentPlaceBinding).apply {
        if (data == null) return@apply
        textTitle.text = data.title
        textUpdateDate.text = data.updateTime
        textContent.text = data.content

        photoList = ArrayList(data.photos?.toList() ?: arrayListOf())

        markerPosition = LatLng(data.latitude, data.longitude)

        if (photoList.isNotEmpty()) {
            photoListAdapter.updateData(photoList)

            listPhoto.apply {
                isVisible = true
                adapter = photoListAdapter
                layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            }
        } else {
            listPhoto.isVisible = false
        }

        btnAction.text = requireActivity().getString(R.string.text_button_close)
        btnAction.setOnClickListener {
            findNavController().navigateUp()
            viewModel.placeData.postValue(null)
        }
    }

    /**
     * 編輯明細
     * @param data  傳入的資料
     */
    private fun editModeCase(data: Place?) = (binding as FragmentPlaceEditModeBinding).apply {
        if (data == null) return@apply
        textTitle.setText(data.title)
        textUpdateDate.text = data.updateTime
        textContent.setText(data.content)

        photoList = ArrayList(data.photos?.toList() ?: arrayListOf())

        markerPosition = LatLng(data.latitude, data.longitude)

        photoListAdapter.apply {
            updateData(photoList, true)

            onItemClick = {
                currentPhotoIndex = it
                showPhotoPickerMenu()
            }

            onItemLongClick = {
                photoList = photoListAdapter.removeData(it)
            }
        }

        listPhoto.apply {
            adapter = photoListAdapter
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        }

        btnAction.text = requireActivity().getString(R.string.text_button_update)
        btnAction.setOnClickListener {
            if (textTitle.text.isNullOrEmpty()) return@setOnClickListener
            val realmList = RealmList<String>().apply { addAll(photoList) }
            val tempData = Place().apply {
                id = dataId
                title = textTitle.text.toString()
                content = textContent.text.toString()
                timestamp = System.currentTimeMillis()
                photos = realmList
                latitude = marker.position.latitude
                longitude = marker.position.longitude
            }
            viewModel.updateData(tempData)
            findNavController().navigateUp()
        }
    }

    /**
     * 新增資料
     */
    @SuppressLint("SimpleDateFormat")
    private fun addModeCase() = (binding as FragmentPlaceEditModeBinding).apply {
        btnAction.text = requireActivity().getString(R.string.text_button_add)
        textUpdateDate.text = SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(System.currentTimeMillis())

        photoListAdapter.apply {
            updateData(photoList, true)

            onItemClick = {
                currentPhotoIndex = it
                showPhotoPickerMenu()
            }

            onItemLongClick = {
                photoList = photoListAdapter.removeData(it)
            }
        }

        listPhoto.apply {
            adapter = photoListAdapter
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        }

        btnAction.setOnClickListener {
            if (textTitle.text.isNullOrEmpty()) return@setOnClickListener
            photoList = photoListAdapter.getPhotoList()
            val realmList = RealmList<String>().apply { addAll(photoList) }
            val tempData = Place().apply {
                title = textTitle.text.toString()
                content = textContent.text.toString()
                timestamp = System.currentTimeMillis()
                photos = realmList
                latitude = marker.position.latitude
                longitude = marker.position.longitude
            }
            viewModel.addData(tempData)
            findNavController().navigateUp()
        }
    }

    /**
     * OnMapReadyCallback
     * @param googleMap  Google Map 物件
     */
    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.isMyLocationEnabled = true

        if (editMode != VIEW_MODE) {
            mMap.setOnMapClickListener { latLng ->
                marker.position = latLng
            }
        }

        initLocation()
    }

    /**
     * 初始化定位服務
     */
    private fun initLocation() {
        val locationUtil = LocationUtil(requireActivity())
        locationUtil.onInitialSuccess = { task ->
            if (task.isSuccessful) {
                val location = task.result
                // 位置服務初始化成功後，再將 Marker 加上
                initMarker(location)
            }
        }
    }

    /**
     * 初始化定位點
     * @param location  GPS位置物件
     */
    private fun initMarker(location: Location?) {
        if (editMode == ADD_MODE && location != null) {
            markerPosition = LatLng(location.latitude, location.longitude)
        }
        val camera = CameraUpdateFactory.newLatLngZoom(markerPosition, mMap.maxZoomLevel)
        mMap.animateCamera(camera)
        val markerOption = MarkerOptions().apply {
            position(markerPosition)
        }
        marker = mMap.addMarker(markerOption)!!
    }

    companion object {
        // UI 初始化模式
        const val VIEW_MODE = 0
        const val EDIT_MODE = 1
        const val ADD_MODE = 2

        // 權限 request code
        const val PICTURE_FROM_GALLERY = 1001
        const val PICTURE_FROM_CAMERA = 1002
    }
}