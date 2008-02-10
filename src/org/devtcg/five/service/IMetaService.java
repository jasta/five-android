/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /home/jasta/android/FiveClient/src/org/devtcg/five/service/IMetaService.aidl
 */
package org.devtcg.five.service;
import java.lang.String;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.BinderNative;
import android.os.Parcel;
public interface IMetaService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.BinderNative implements org.devtcg.five.service.IMetaService
{
private static final java.lang.String DESCRIPTOR = "org.devtcg.five.service.IMetaService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an IMetaService interface,
 * generating a proxy if needed.
 */
public static org.devtcg.five.service.IMetaService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
org.devtcg.five.service.IMetaService in = (org.devtcg.five.service.IMetaService)obj.queryLocalInterface(DESCRIPTOR);
if ((in!=null)) {
return in;
}
return new org.devtcg.five.service.IMetaService.Stub.Proxy(obj);
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
case TRANSACTION_registerObserver:
{
org.devtcg.five.service.IMetaObserver _arg0;
_arg0 = org.devtcg.five.service.IMetaObserver.Stub.asInterface(data.readStrongBinder());
this.registerObserver(_arg0);
return true;
}
case TRANSACTION_unregisterObserver:
{
org.devtcg.five.service.IMetaObserver _arg0;
_arg0 = org.devtcg.five.service.IMetaObserver.Stub.asInterface(data.readStrongBinder());
this.unregisterObserver(_arg0);
return true;
}
case TRANSACTION_startSync:
{
boolean _result = this.startSync();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_stopSync:
{
boolean _result = this.stopSync();
reply.writeInt(((_result)?(1):(0)));
return true;
}
}
}
catch (android.os.DeadObjectException e) {
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.devtcg.five.service.IMetaService
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
public void registerObserver(org.devtcg.five.service.IMetaObserver observer) throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeStrongBinder((((observer!=null))?(observer.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_registerObserver, _data, null, 0);
}
finally {
_data.recycle();
}
}
public void unregisterObserver(org.devtcg.five.service.IMetaObserver observer) throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeStrongBinder((((observer!=null))?(observer.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_unregisterObserver, _data, null, 0);
}
finally {
_data.recycle();
}
}
public boolean startSync() throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
mRemote.transact(Stub.TRANSACTION_startSync, _data, _reply, 0);
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
public boolean stopSync() throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
mRemote.transact(Stub.TRANSACTION_stopSync, _data, _reply, 0);
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_registerObserver = (IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_unregisterObserver = (IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_startSync = (IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_stopSync = (IBinder.FIRST_CALL_TRANSACTION + 3);
}
public void registerObserver(org.devtcg.five.service.IMetaObserver observer) throws android.os.DeadObjectException;
public void unregisterObserver(org.devtcg.five.service.IMetaObserver observer) throws android.os.DeadObjectException;
public boolean startSync() throws android.os.DeadObjectException;
public boolean stopSync() throws android.os.DeadObjectException;
}
