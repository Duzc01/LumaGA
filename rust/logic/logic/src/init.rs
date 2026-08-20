pub(crate) fn init() {
    #[cfg(not(target_os = "android"))]
    env_logger::builder()
        .filter_level(if cfg!(debug_assertions) {
            log::LevelFilter::Trace
        } else {
            log::LevelFilter::Info
        })
        .filter_module("sled", log::LevelFilter::Info) // too verbose
        .init();

    if cfg!(debug_assertions) {
        unsafe { std::env::set_var("RUST_BACKTRACE", "1") };
    }

    #[cfg(target_os = "android")]
    {
        // `with_min_level(L)` in android_logger 0.11 gates on `level >= L`
        // while also setting log's max level to `L`, so the two filters
        // intersect to exactly one level: passing `Debug` silently dropped
        // every `info!`/`warn!`/`error!`. Leave the config's level unset (its
        // filter then passes everything) and set log's max level ourselves.
        android_logger::init_once(android_logger::Config::default());
        log::set_max_level(log::LevelFilter::Debug);
    }

    log::info!("initialized logic");
}
