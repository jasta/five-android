/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /home/jasta/android/FiveClient/src/org/devtcg/five/service/IMetaObserver.aidl
 */
package org.devtcg.five.service;
import java.lang.String;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.BinderNative;
import android.os.Parcel;
public interface IMetaObserver extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.BinderNative implements org.devtcg.five.service.IMetaObserver
{
private static final java.lang.String DESCRIPTOR = "org.devtcg.five.service.IMetaObserver";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an IMetaObserver interface,
 * generating a proxy if needed.
 */
public static org.devtcg.five.service.IMetaObserver asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
org.devtcg.five.service.IMetaObserver in = (org.devtcg.five.service.IMetaObserver)obj.queryLocalInterface(DESCRIPTOR);
if ((in!=null)) {
return in;
}
return new org.devtcg.five.service.IMetaObserver.Stub.Proxy(obj);
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
case TRANSACTION_beginSync:
{
this.beginSync();
return true;
}
case TRANSACTION_endSync:
{
this.endSync();
return true;
}
case TRANSACTION_beginSource:
{
int _arg0;
_arg0 = data.readInt();
this.beginSource(_arg0);
return true;
}
case TRANSACTION_endSource:
{
int _arg0;
_arg0 = data.readInt();
this.endSource(_arg0);
return true;
}
case TRANSACTION_updateProgress:
{
int _arg0;
_arg0 = data.readInt();
int _arg1;
_arg1 = data.readInt();
int _arg2;
_arg2 = data.readInt();
this.updateProgress(_arg0, _arg1, _arg2);
return true;
}
}
}
catch (android.os.DeadObjectException e) {
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.devtcg.five.service.IMetaObserver
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
public void beginSync() throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
mRemote.transact(Stub.TRANSACTION_beginSync, _data, null, 0);
}
finally {
_data.recycle();
}
}
public void endSync() throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
mRemote.transact(Stub.TRANSACTION_endSync, _data, null, 0);
}
finally {
_data.recycle();
}
}
public void beginSource(int sourceId) throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInt(sourceId);
mRemote.transact(Stub.TRANSACTION_beginSource, _data, null, 0);
}
finally {
_data.recycle();
}
}
public void endSource(int sourceId) throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInt(sourceId);
mRemote.transact(Stub.TRANSACTION_endSource, _data, null, 0);
}
finally {
_data.recycle();
}
}
public void updateProgress(int sourceId, int itemNo, int itemCount) throws android.os.DeadObjectException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInt(sourceId);
_data.writeInt(itemNo);
_data.writeInt(itemCount);
mRemote.transact(Stub.TRANSACTION_updateProgress, _data, null, 0);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_beginSync = (IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_endSync = (IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_beginSource = (IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_endSource = (IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_updateProgress = (IBinder.FIRST_CALL_TRANSACTION + 4);
}
public void beginSync() throws android.os.DeadObjectException;
public void endSync() throws android.os.DeadObjectException;
public void beginSource(int sourceId) throws android.os.DeadObjectException;
public void endSource(int sourceId) throws android.os.DeadObjectException;
public void updateProgress(int sourceId, int itemNo, int itemCount) throws android.os.DeadObjectException;
}
