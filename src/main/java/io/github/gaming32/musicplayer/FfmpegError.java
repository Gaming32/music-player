package io.github.gaming32.musicplayer;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import static com.sun.jna.platform.linux.ErrNo.*;

// https://ffmpeg.org/doxygen/trunk/error_8h_source.html
// https://ffmpeg.org/doxygen/trunk/error_8c_source.html
// NOTE: These errors are ANDed with 0xff because ffmpeg does that on the CLI
public class FfmpegError {
    public static final int BSF_NOT_FOUND = create(0xF8, "BSF");
    public static final int BUG = create("BUG!");
    public static final int BUFFER_TOO_SMALL = create("BUFS");
    public static final int DECODER_NOT_FOUND = create(0xF8, "DEC");
    public static final int DEMUXER_NOT_FOUND = create(0xF8, "DEM");
    public static final int ENCODER_NOT_FOUND = create(0xF8, "ENC");
    public static final int EOF = create("EOF ");
    public static final int EXIT = create("EXIT");
    public static final int EXTERNAL = create("EXT ");
    public static final int FILTER_NOT_FOUND = create(0xF8, "FIL");
    public static final int INVALIDDATA = create("INDA");
    public static final int MUXER_NOT_FOUND = create(0xF8, "MUX");
    public static final int OPTION_NOT_FOUND = create(0xF8, "OPT");
    public static final int PATCHWELCOME = create("PAWE");
    public static final int PROTOCOL_NOT_FOUND = create(0xF8, "PRO");
    public static final int STREAM_NOT_FOUND = create(0xF8, "STR");
    public static final int BUG2 = create("BUG ");
    public static final int UNKNOWN = create("UNKN");
    public static final int EXPERIMENTAL = -0x2bb2afa8 & 0xff;
    public static final int INPUT_CHANGED = -0x636e6701 & 0xff;
    public static final int OUTPUT_CHANGED = -0x636e6702 & 0xff;
    public static final int HTTP_BAD_REQUEST = create(0xF8, "400");
    public static final int HTTP_UNAUTHORIZED = create(0xF8, "401");
    public static final int HTTP_FORBIDDEN = create(0xF8, "403");
    public static final int HTTP_NOT_FOUND = create(0xF8, "404");
    public static final int HTTP_OTHER_4XX = create(0xF8, "4XX");
    public static final int HTTP_SERVER_ERROR = create(0xF8, "5XX");

    private static final int INPUT_AND_OUTPUT_CHANGED = INPUT_CHANGED | OUTPUT_CHANGED;
    private static final Int2ObjectMap<String> ERROR_MESSAGES = createMessagesMap(
        // ffmpeg errors
        BSF_NOT_FOUND,      "Bitstream filter not found",
        BUG,                "Internal bug, should not have happened",
        BUG2,               "Internal buf, should not have happened",
        BUFFER_TOO_SMALL,   "Buffer too small",
        DECODER_NOT_FOUND,  "Decoder not found",
        DEMUXER_NOT_FOUND,  "Demuxer not found",
        ENCODER_NOT_FOUND,  "Encoder not found",
        EOF,                "End of file",
        EXIT,               "Immediate exit requested",
        EXTERNAL,           "Generic error in an external library",
        FILTER_NOT_FOUND,   "Filter not found",
        INPUT_CHANGED,      "Input changed",
        INVALIDDATA,        "Invalid data found when processing input",
        MUXER_NOT_FOUND,    "Muxer not found",
        OPTION_NOT_FOUND,   "Option not found",
        OUTPUT_CHANGED,     "Output changed",
        PATCHWELCOME,       "Not yet implemented in FFmpeg, patches welcome",
        PROTOCOL_NOT_FOUND, "Protocol not found",
        STREAM_NOT_FOUND,   "Stream not found",
        UNKNOWN,            "Unknown error occurred",
        EXPERIMENTAL,       "Experimental feature",
        INPUT_AND_OUTPUT_CHANGED, "Input and output changed",
        HTTP_BAD_REQUEST,   "Server returned 400 Bad Request",
        HTTP_UNAUTHORIZED,  "Server returned 401 Unauthorized (authorization failed)",
        HTTP_FORBIDDEN,     "Server returned 403 Forbidden (access denied)",
        HTTP_NOT_FOUND,     "Server returned 404 Not Found",
        HTTP_OTHER_4XX,     "Server returned 4XX Client Error, but not one of 40{0,1,3,4}",
        HTTP_SERVER_ERROR,  "Server returned 5XX Server Error reply",
        // System errors
        E2BIG,              "Argument list too long",
        EACCES,             "Permission denied",
        EAGAIN,             "Resource temporarily unavailable",
        EBADF,              "Bad file descriptor",
        EBUSY,              "Device or resource busy",
        ECHILD,             "No child processes",
        EDEADLK,            "Resource deadlock avoided",
        EDOM,               "Numerical argument out of domain",
        EEXIST,             "File exists",
        EFAULT,             "Bad address",
        EFBIG,              "File too large",
        EILSEQ,             "Illegal byte sequence",
        EINTR,              "Interrupted system call",
        EINVAL,             "Invalid argument",
        EIO,                "I/O error",
        EISDIR,             "Is a directory",
        EMFILE,             "Too many open files",
        EMLINK,             "Too many links",
        ENAMETOOLONG,       "File name too long",
        ENFILE,             "Too many open files in system",
        ENODEV,             "No such device",
        ENOENT,             "No such file or directory",
        ENOEXEC,            "Exec format error",
        ENOLCK,             "No locks available",
        ENOMEM,             "Cannot allocate memory",
        ENOSPC,             "No space left on device",
        ENOSYS,             "Function not implemented",
        ENOTDIR,            "Not a directory",
        ENOTEMPTY,          "Directory not empty",
        ENOTTY,             "Inappropriate I/O control operation",
        ENXIO,              "No such device or address",
        EPERM,              "Operation not permitted",
        EPIPE,              "Broken pipe",
        ERANGE,             "Result too large",
        EROFS,              "Read-only file system",
        ESPIPE,             "Illegal seek",
        ESRCH,              "No such process",
        EXDEV,              "Cross-device link"
    );

    public static String toString(int errNum) {
        final String message = ERROR_MESSAGES.get(errNum);
        return message != null ? message : "Error number " + errNum + " occurred";
    }

    private static Int2ObjectMap<String> createMessagesMap(Object... messages) {
        final Int2ObjectMap<String> result = new Int2ObjectOpenHashMap<>(messages.length / 2);
        for (int i = 0; i < messages.length; i += 2) {
            result.putIfAbsent((int)(Integer)messages[i], (String)messages[i + 1]);
        }
        return result;
    }

    private static int create(String s) {
        return create(s.charAt(0), s.charAt(1), s.charAt(2), s.charAt(3));
    }

    private static int create(int a, String s) {
        return create(a, s.charAt(0), s.charAt(1), s.charAt(2));
    }

    private static int create(int a, int b, int c, int d) {
        return -(a | b << 8 | c << 16 | d << 24) & 0xff;
    }
}
