package com.kyhsgeekcode

import android.content.ClipData
import android.content.res.Resources
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import at.pollaknet.api.facile.Facile
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.io.FileUtils
import splitties.init.appCtx
import splitties.systemservices.clipboardManager
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt


fun extractZip(from: File, toDir: File, publisher: (Long, Long) -> Unit = { _, _ -> }) {
    val zi = ZipInputStream(from.inputStream())
    var entry: ZipEntry
    val buffer = ByteArray(2048)
    var processed = 0L
    val total = from.length()
    while (zi.nextEntry.also { entry = it } != null) {
        val name = entry.name
        val outfile = File(toDir, name)
        outfile.delete()
        outfile.parentFile.mkdirs()
        val canonicalPath: String = outfile.canonicalPath
        if (!canonicalPath.startsWith(toDir.canonicalPath)) {
            throw SecurityException("The zip/apk file may have a Zip Path Traversal Vulnerability." +
                    "Is the zip/apk file trusted?")
        }
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(outfile)
            var len: Int
            while (zi.read(buffer).also { len = it } > 0) {
                output.write(buffer, 0, len)
            }
        } finally { // we must always close the output file
            output?.close()
        }
        processed += entry.size
        publisher(total, processed)
        zi.close()
    }
}

fun File.isArchive(): Boolean {
    return try {
        ArchiveStreamFactory().createArchiveInputStream(BufferedInputStream(inputStream()))
        true
    } catch (e: Exception) {
        false
    }
}

fun File.isDotnetFile(): Boolean {
    return try {
        Facile.load(path)
        true
    } catch (e: Exception) {
        false
    }
}

fun File.isDexFile(): Boolean = extension.toLowerCase() == "dex"

fun File.isAccessible(): Boolean = exists() && canRead()

fun extract(from: File, toDir: File, publisher: (Long, Long) -> Unit = { _, _ -> }) {
    Log.v("extract", "File:${from.path}")
    try {
        val archi = ArchiveStreamFactory().createArchiveInputStream(BufferedInputStream(from.inputStream()))
        var entry: ArchiveEntry?
        while (archi.nextEntry.also { entry = it } != null) {
            if (!archi.canReadEntryData(entry)) {
                // log something?
                Log.e("Extract archive", "Cannot read entry data")
                continue
            }
            val f = toDir.resolve(entry?.name!!)
            if (entry!!.isDirectory) {
                if (!f.isDirectory && !f.mkdirs()) {
                    throw  IOException("failed to create directory $f")
                }
            } else {
                val parent = f.parentFile
                if (!parent.isDirectory && !parent.mkdirs()) {
                    throw  IOException("failed to create directory $parent")
                }
                if (!f.canonicalPath.startsWith(toDir.canonicalPath)) {
                    throw SecurityException("The zip/apk file may have a Zip Path Traversal Vulnerability." +
                            "Is the zip/apk file trusted?")
                }
                val o = f.outputStream()
                IOUtils.copy(archi, o)
            }
        }
    } catch (e: ArchiveException) {
        Log.e("Extract archive", "error inflating", e)
    } catch (e: ZipException) {
        Log.e("Extract archive", "error inflating", e)
    }
}

fun String.toValidFileName(): String {
    return this.replace("[\\\\/:*?\"<>|]", "")
}

//MAYBE BUG : relName to entry name
fun saveAsZip(dest: File, vararg sources: Pair<String, String>) {
    val archiveStream: OutputStream = FileOutputStream(dest)
    val archive = ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.ZIP, archiveStream)
    for (source in sources) {
        val from = source.first
        val to = source.second
        val fromFile = File(from)
        val toFile = File(to)
        if (fromFile.isDirectory) {
            val fileList = FileUtils.listFiles(fromFile, null, true)
            for (file in fileList) {
                val relName: String = getEntryName(fromFile, file)
                val splitName = relName.split(File.separatorChar)
                val entryName = toFile.resolve(splitName.subList(1, splitName.size - 1).joinToString(File.separator)).absolutePath
                val entry = ZipArchiveEntry(entryName)
                archive.putArchiveEntry(entry)
                val input = BufferedInputStream(FileInputStream(file))
                IOUtils.copy(input, archive)
                input.close()
                archive.closeArchiveEntry()
            }
        } else {
            val entryName = to
            val entry = ZipArchiveEntry(entryName)
            archive.putArchiveEntry(entry)
            val input = BufferedInputStream(FileInputStream(fromFile))
            IOUtils.copy(input, archive)
            input.close()
            archive.closeArchiveEntry()
        }
    }
}

/**
 * Remove the leading part of each entry that contains the source directory name
 *
 * @param source the directory where the file entry is found
 * @param file   the file that is about to be added
 * @return the name of an archive entry
 * @throws IOException if the io fails
 * @author http://www.thinkcode.se/blog/2015/08/21/packaging-a-zip-file-from-java-using-apache-commons-compress
 */
@Throws(IOException::class)
fun getEntryName(source: File, file: File): String {
    val index: Int = source.absolutePath.length + 1
    val path = file.canonicalPath
    return path.substring(index)
}


//https://stackoverflow.com/a/6425744/8614565
fun deleteRecursive(fileOrDirectory: File) {
    if (fileOrDirectory.isDirectory) for (child in fileOrDirectory.listFiles()) deleteRecursive(child)
    fileOrDirectory.delete()
}

fun setClipBoard(s: String?) {
    val clip = ClipData.newPlainText("Android Disassembler", s)
    clipboardManager.setPrimaryClip(clip)
}

private fun getRealPathFromURI(uri: Uri): String {
    var filePath: String
    filePath = uri.path ?: return ""
    //경로에 /storage가 들어가면 real file path로 판단
    if (filePath.startsWith("/storage")) return filePath
    val wholeID = DocumentsContract.getDocumentId(uri)
    //wholeID는 파일명이 abc.zip이라면 /document/B5D7-1CE9:abc.zip와 같습니다.
// Split at colon, use second item in the array
    val id = wholeID.split(":").toTypedArray()[0]
    //Log.e(TAG, "id = " + id);
    val column = arrayOf(MediaStore.Files.FileColumns.DATA)
    //파일의 이름을 통해 where 조건식을 만듭니다.
    val sel = MediaStore.Files.FileColumns.DATA + " LIKE '%" + id + "%'"
    //External storage에 있는 파일의 DB를 접근하는 방법 입니다.
    val cursor = appCtx.contentResolver.query(MediaStore.Files.getContentUri("external"), column, sel, null, null)
            ?: return ""
    //SQL문으로 표현하면 아래와 같이 되겠죠????
//SELECT _dtat FROM files WHERE _data LIKE '%selected file name%'
    val columnIndex = cursor.getColumnIndex(column[0])
    if (cursor.moveToFirst()) {
        filePath = cursor.getString(columnIndex)
    }
    cursor.close()
    return filePath
}

//https://stackoverflow.com/a/48351453/8614565
fun convertDpToPixel(dp: Float): Int {
    val metrics = Resources.getSystem().displayMetrics
    val px = dp * (metrics.densityDpi / 160f)
    return px.roundToInt()
}