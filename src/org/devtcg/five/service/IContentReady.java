/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /home/jasta/android/FiveClient/src/org/devtcg/five/service/IContentReady.aidl
 */
package org.devtcg.five.service;
import java.lang.String;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.BinderNative;
import android.os.Parcel;
//import IContentState;

public interface IContentReady extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.BinderNative implements org.devtcg.five.service.IContentReady
{
private static final java.lang.String DESCRIPTOR = "org.devtcg.five.service.IContentReady";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an IContentReady interface,
 * generating a proxy if needed.
 */
public static org.devtcg.five.service.IContentReady asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
org.devtcg.five.service.IContentReady in = (org.devtcg.five.service.IContentReady)obj.queryLocalInterface(DESCRIPTOR);
if ((in!=null)) {
return in;
}
return new org.devtcg.five.service.IContentReady.Stub.Proxy(obj);
}
public android.os.IBinder asBinder()
{
return this;
}
public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
{
try {
switch (code)
{
case TRANSACTION_ready:
{
int _arg0;
_arg0 = data.readInt();
java.lang.String _arg1;
_arg1 = data.readString();
this.ready(_arg0, _arg1);
return true;
}
}
}
catch (android.os.DeadObjectException e) {
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.devtcg.five.service.IContentReady
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
public android.os.IBinder asBinder()
{
return mRemote;
}
public void ready(int contentId, java.lang.String uri) throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInt(contentId);
_data.writeString(uri);
mRemote.transact(Stub.TRANSACTION_ready, _data, null, 0);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_ready = (IBinder.FIRST_CALL_TRANSACTION + 0);
}
public void ready(int contentId, java.lang.String uri) throws android.os.DeadObjectException;
}
