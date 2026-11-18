package com.disiplinai.service

import android.app.admin.DeviceAdminReceiver

/** Hanya untuk mempersulit uninstall: aplikasi admin harus dinonaktifkan dulu sebelum bisa dihapus. */
class AdminReceiver : DeviceAdminReceiver()
