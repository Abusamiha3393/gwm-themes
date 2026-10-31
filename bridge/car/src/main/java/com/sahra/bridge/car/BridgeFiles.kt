package com.sahra.bridge.car

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * يعرض الملفات المستلمة لتطبيقات الشاشة الأخرى (المشغّل، عارض PDF…) كروابط content://
 * بصلاحية قراءة مؤقتة، بدل كشف مسار الملف. بديل صغير عن FileProvider دون AndroidX.
 */
class BridgeFiles : ContentProvider() {
    override fun onCreate() = true

    private fun fileFor(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException()
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        val root = Uploads.received(ctx)
        val f = File(root, name)
        // لا نسمح بالخروج من مجلد المستلمات
        if (f.parentFile?.canonicalPath != root.canonicalPath || !f.exists()) throw FileNotFoundException(name)
        return f
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun getType(uri: Uri): String = Uploads.guessMime(uri.lastPathSegment ?: "")

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        val f = fileFor(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = cols.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }.toTypedArray()
        return MatrixCursor(cols, 1).apply { addRow(row) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        fun authority(context: Context) = "${context.packageName}.files"

        fun uriFor(context: Context, file: File): Uri =
            Uri.Builder().scheme("content").authority(authority(context)).appendPath(file.name).build()

        fun fileOf(context: Context, uri: Uri): File? {
            val name = uri.lastPathSegment ?: return null
            return File(Uploads.received(context), name).takeIf { it.exists() }
        }
    }
}
