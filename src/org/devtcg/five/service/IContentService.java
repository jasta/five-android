/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /home/jasta/android/FiveClient/src/org/devtcg/five/service/IContentService.aidl
 */
package org.devtcg.five.service;
import java.lang.String;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.BinderNative;
import android.os.Parcel;
public interface IContentService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.BinderNative implements org.devtcg.five.service.IContentService
{
private static final java.lang.String DESCRIPTOR = "org.devtcg.five.service.IContentService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an IContentService interface,
 * generating a proxy if needed.
 */
public static org.devtcg.five.service.IContentService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
org.devtcg.five.service.IContentService in = (org.devtcg.five.service.IContentService)obj.queryLocalInterface(DESCRIPTOR);
if ((in!=null)) {
return in;
}
return new org.devtcg.five.service.IContentService.Stub.Proxy(obj);
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
case TRANSACTION_getContent:
{
int _arg0;
_arg0 = data.readInt();
org.devtcg.five.service.IContentReady _arg1;
_arg1 = org.devtcg.five.service.IContentReady.Stub.asInterface(data.readStrongBinder());
boolean _result = this.getContent(_arg0, _arg1);
reply.writeInt(((_result)?(1):(0)));
return true;
}
}
}
catch (android.os.DeadObjectException e) {
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.devtcg.five.service.IContentService
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
public boolean getContent(int id, org.devtcg.five.service.IContentReady callback) throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInt(id);
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_getContent, _data, _reply, 0);
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_getContent = (IBinder.FIRST_CALL_TRANSACTION + 0);
}
public boolean getContent(int id, org.devtcg.five.service.IContentReady callback) throws android.os.DeadObjectException;
}
