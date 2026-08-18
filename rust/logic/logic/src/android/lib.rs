use crate::{
    android::callback::AndroidCallback, r#async::serve_request_async,
    callback_trait::CallbackTrait, sync::serve_request_sync,
};
use jni::{
    JNIEnv,
    objects::{JClass, JObject},
    sys::jbyteArray,
};
use protos::{
    Message,
    Service::{AsyncRequest, SyncRequest},
};

fn may_init() {
    static INIT: std::sync::Once = std::sync::Once::new();
    INIT.call_once(crate::init::init);
}

fn parse_from_j<T: Message>(env: &JNIEnv, data: jbyteArray) -> T {
    let bytes = env.convert_byte_array(data).unwrap();
    T::parse_from_bytes(&bytes).expect("invalid request")
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_bugenzhao_mnga_LogicKt_rustCall(
    env: JNIEnv,
    _: JClass,
    data: jbyteArray,
) -> jbyteArray {
    may_init();
    let request = parse_from_j::<SyncRequest>(&env, data);
    log::info!("request {:?}", request);
    let response_buf = serve_request_sync(request);

    match response_buf {
        Ok(data) => env.byte_array_from_slice(&data).unwrap(),
        Err(err) => {
            env.throw(err.to_app_string()).unwrap();
            unreachable!()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_bugenzhao_mnga_LogicKt_rustCallAsync(
    env: JNIEnv,
    _: JClass,
    data: jbyteArray,
    jcallback: JObject,
) {
    may_init();
    let request = parse_from_j::<AsyncRequest>(&env, data);
    let callback = AndroidCallback::new(&env, jcallback);
    log::info!("async request #{:?} {:?}", callback.id(), request);
    serve_request_async(request, callback);
}
