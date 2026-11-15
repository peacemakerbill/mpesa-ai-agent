// What differs between the phone, the desktop and the browser when a chat file is saved, opened or discarded.
// dart:io, path_provider and open_filex do not exist in a browser, so each place has its own file.
export 'device_files_stub.dart'
    if (dart.library.io) 'device_files_io.dart'
    if (dart.library.js_interop) 'device_files_web.dart';
