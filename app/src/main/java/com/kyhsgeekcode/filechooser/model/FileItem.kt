package com.kyhsgeekcode.filechooser.model

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Environment
import android.util.Log
import com.kyhsgeekcode.*
import splitties.init.appCtx
import java.io.File
import java.io.Serializable

//A item of base, or a file
open class FileItem : Serializable {
    val TAG = "FileItem"

    constructor(text: String, drawable: Drawable? = null) {
        this.text = text
        this.drawable = drawable
    }

    constructor(text: String? = null, file: File, drawable: Drawable? = null) {
        this.text = text ?: file.name
        this.file = file
        this.drawable = drawable
    }


    var text: String = ""

    @Transient
    var drawable: Drawable? = null

    var file: File? = null  //항목이 의미하는 파일 자체
    var backFile: File? = null  //항목이 전개되었을 때 나타낼 디렉토리

    val isExpandable: Boolean by lazy {
        file?.isDirectory == true ||
                file?.isArchive() == true ||
                file == null ||
                file?.isDotnetFile() == true ||
                file?.isDexFile() == true
    }

    open fun canExpand(): Boolean {
        return isExpandable && isAccessible()
    }

    open fun isRawAvailable(): Boolean = file?.isDirectory ?: true

    open fun isAccessible(): Boolean = file?.isAccessible() ?: true

    open fun listSubItems(publisher: (Int, Int) -> Unit = { _, _ -> }): List<FileItem> {
        if (!canExpand())
            return emptyList()
        if (file?.isDirectory == true) {
            val result = ArrayList<FileItem>()
            for (childFile in file!!.listFiles()) {
                result.add(FileItem(file = childFile))
            }
            return result
        } else if (file?.isArchive() == true) {
            val result = ArrayList<FileItem>()
            backFile = appCtx.getExternalFilesDir("extracted")?.resolve(file?.name!!)
            if(backFile?.exists() == true) {
                backFile!!.delete()
            }
            extract(file!!, backFile!!){tot,don->publisher(tot.toInt(),don.toInt())}
            for(childFile in backFile!!.listFiles()) {
                result.add(FileItem(file=childFile))
            }
            return result
        }
        return emptyList()
    }

    fun listSubItemsFile(parent: File): List<FileItem> {
        val result = ArrayList<FileItem>()
        for (file: File in parent.listFiles()) {
            result.add(FileItem(file = file))
        }
        return result
    }

    companion object {
        val rootItem = object : FileItem("Main") {
            override fun listSubItems(publisher: (Int, Int) -> Unit): List<FileItem> {
                return listOf(fileRoot, fileSdcard, apps, processes)
            }

            override fun canExpand(): Boolean = true
        }

        val fileRoot = FileItem(file = File("/"))

        val fileSdcard = FileItem(file = Environment.getExternalStorageDirectory())

        val apps = object : FileItem("Apps", appCtx.getDrawable(android.R.drawable.sym_def_app_icon)) {
            override fun canExpand(): Boolean = true
            override fun listSubItems(publisher: (Int, Int) -> Unit): List<FileItem> {
                val result = ArrayList<FileItem>()
                val pm: PackageManager = appCtx.packageManager
                //get a list of installed apps.
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val numpkg = packages.size
                //dialog.setMessage("Sorting APKs...")
                publisher(numpkg * 2, 0)
                packages.sortBy {
                    pm.getApplicationLabel(it) as String
                }
                publisher(numpkg * 2, numpkg)
                var i = 0
                val defaultD: Drawable? = appCtx.getDrawable(android.R.drawable.gallery_thumb)
                for (packageInfo in packages) { //Log.d(TAG, "Installed package :" + packageInfo.packageName);
//Log.d(TAG, "Apk file path:" + packageInfo.sourceDir);
                    val applabel = pm.getApplicationLabel(packageInfo) as String
                    var icon: Drawable? = defaultD
                    try {
                        icon = pm.getApplicationIcon(packageInfo.packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.e(TAG, "", e)
                    }
                    val label = applabel + "(" + packageInfo.packageName + ")"
                    result.add(FileItemApp(label, File(packageInfo.sourceDir), icon))
                    i++
                    if (i % 10 == 0) {
                        publisher(numpkg * 2, i + numpkg)
                    }
                }
                return result
            }
        }

        val processes = object : FileItem("Processes") {
            override fun listSubItems(publisher: (Int, Int) -> Unit): List<FileItem> {
                return listOf(FileItem("Currently unavailable"))
            }
        }
    }
}

