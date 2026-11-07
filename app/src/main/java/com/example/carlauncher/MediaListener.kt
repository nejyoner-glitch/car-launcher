package com.example.carlauncher

import android.service.notification.NotificationListenerService

/**
 * Boş bir bildirim dinleyicisi. Android, aktif medya oturumlarını (Spotify, YouTube Music vb.)
 * okuyabilmemiz için uygulamanın "Bildirim erişimi" almasını şart koşar.
 */
class MediaListener : NotificationListenerService()
