package tk.netindev.zeronet.data

import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.os.Parcel

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false
) : Parcelable {
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageName)
        parcel.writeString(appName)
        parcel.writeByte(if (isSystemApp) 1 else 0)
    }
    
    override fun describeContents(): Int {
        return 0
    }
    
    companion object CREATOR : Parcelable.Creator<AppInfo> {
        override fun createFromParcel(parcel: Parcel): AppInfo {
            return AppInfo(
                packageName = parcel.readString() ?: "",
                appName = parcel.readString() ?: "",
                icon = null,
                isSystemApp = parcel.readByte() != 0.toByte()
            )
        }
        
        override fun newArray(size: Int): Array<AppInfo?> {
            return arrayOfNulls(size)
        }
    }
}
