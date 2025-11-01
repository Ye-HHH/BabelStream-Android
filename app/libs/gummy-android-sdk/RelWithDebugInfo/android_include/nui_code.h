/* Copyright [2020] <joseph.zgd@alibaba-inc.com> */
#ifndef INTERNAL_NUI_CODE_H_
#define INTERNAL_NUI_CODE_H_

#define NUI_API_INCLUDE_DIALOG 1
#define NUI_API_INCLUDE_TINGWU 1
#define NUI_API_INCLUDE_DASHSCOPE 1
#define NUI_API_INCLUDE_TTS 1
#define NUI_API_INCLUDE_STREAM_INPUT_TTS 1
#define NUI_API_INCLUDE_ST 1
#define NUI_API_INCLUDE_FILE_TRANS 1

namespace PRODUCT_API_NAMESPACE {
enum NuiSdkLogLevel {
  LOG_LEVEL_VERBOSE,
  LOG_LEVEL_DEBUG,
  LOG_LEVEL_INFO,
  LOG_LEVEL_WARNING,
  LOG_LEVEL_ERROR,
  LOG_LEVEL_NONE,
};

enum NuiAudioState {
  STATE_OPEN,
  STATE_PAUSE,
  STATE_CLOSE,
};

enum NuiFunMode {
  kFuncModeAsp = 0x00000001,
  kFuncModeVad = 0x00000010,
  kFuncModeKws = 0x00000100,
  kFuncModeAsr = 0x00001000,
  kFuncModeEnc = 0x00010000,
  kFuncModeOu = 0x00100000,
  kFuncModeFull = 0x11111111,
};

enum NuiAudioExtraEvent {
  AudioRmsChanged,
};

typedef void (*FuncAsyncCallback)(void *user_data, int result,
                                  const char *ex_info);
struct NuiAsyncCallback {
  NuiAsyncCallback() : async_callback(nullptr), user_data(nullptr) {}

  FuncAsyncCallback async_callback;
  void *user_data;
};

typedef int NuiResultCode;
const int SUCCESS = 0;
// legacy, not required handle
const int DEFAULT_ERROR = 240999;  // default err value.

// config or params invalid
const int NUI_CONFIG_INVALID = 240001;    // Config or file invalid.
const int ILLEGAL_PARAM = 240002;         // parmeters illegal.
const int ILLEGAL_INIT_PARAM = 240003;    // initialize with illegal parameters.
const int NECESSARY_PARAM_LACK = 240004;  // lack of necessaray parameters.
const int NULL_PARAM_ERROR =
    240005;  // parameters with null pointer or empty content.
const int NULL_LISTENER_ERROR = 240006;  // no listener for callback set.
const int NULL_DIALOG_ERROR = 240007;    // no dialog created.
const int NULL_ENGINE_ERROR = 240008;    // no dialog engine created.
const int ILLEGAL_DATA = 240009;        // transfer illegal audio or video data.
const int ILLEGAL_WORD_LIST = 2400010;  // word list format error
// state invalid
const int ILLEGAL_REENTRANT =
    240010;  // happen when call from async callback or sdk already exit.
const int SDK_NOT_INIT = 240011;      // call apis when sdk not initialized.
const int SDK_ALREADY_INIT = 240012;  // call initialize if already initialized.
const int DIALOG_INVALID_STATE = 240013;  // dialog state machine error.
const int STATE_INVALID = 240014;         // asr engine state machine error.
const int ILLEGAL_FUNC_CALL =
    240015;  // some function is not supposed to call on some working mode.

// system call fail
const int MEM_ALLOC_ERROR = 240020;   // alloc or new object fail.
const int FILE_ACCESS_FAIL = 240021;  // access file fail.
const int CREATE_DIR_ERROR = 240022;  // create directory fail.

// nui call fail
const int CREATE_NUI_ERROR = 240030;         // create dialog engine fail.
const int TEXT_DIALOG_START_FAIL = 240031;   // start text to action fail.
const int TEXT_DIALOG_CANCEL_FAIL = 240032;  // cancel text to action fail.
const int WUW_DUPLICATE =
    240033;  // set duplicated wake words for dynamic keywords.

// cei call fail
const int CEI_INIT_FAIL = 240040;             // init cei engine fail.
const int CEI_SET_PARAM_FAIL = 240041;        // cei set parameters fail.
const int CEI_COMPILE_GRAMMAR_FAIL = 240042;  // cei compile grammar fail.
const int CEI_STOP_FAIL = 240043;             // cei stop fail.
const int CEI_CANCEL_FAIL = 240044;           // cei cancel fail.
const int CEI_UNLOAD_KWS_FAIL = 240045;       // cei unload kws fail.
const int GET_WUW_ERROR = 240046;             // cei get wakeup word fail.
const int CEI_CHECK_BIN_FAIL = 240047;        // check resource bin file fail

// audio manager
const int SELECT_RECORDER_ERROR = 240050;  // select recorder fail.
const int UPDATE_AUDIO_ERROR =
    240051;  // update audio fail; usually push audio more than wanted.
const int MIC_ERROR = 240052;  // get audio data fail continually for 2
                               // senconds.

// nls error
const int CREATE_DA_REQUEST_ERROR = 240060;  // create dialog assistant fail.
const int START_DA_REQUEST_ERROR = 240061;   // start dialog assistant fail.
const int DEFAULT_NLS_ERROR = 240062;        // default network error.
const int SSL_ERROR = 240063;                // ssl new fail.
const int SSL_CONNECT_FAILED = 240064;       // ssl connect fail.
const int HTTP_CONNECT_FAILED = 240065;      // http connect fail.
const int DNS_FAILED = 240066;               // DNS fail.
const int CONNECT_FAILED = 240067;           // connect socket fail.
const int SERVER_NOT_ACCESS = 240068;        // server responce status bad.
const int SOCKET_CLOSED = 240069;            // socket closed.
const int AUTH_FAILED = 240070;              // authentication fail.
const int HTTPDNS_FAILED = 240071;           // connect fail when use http dns.
const int HTTP_SEND_FAILED = 240072;
const int HTTP_RECEIVE_FAILED = 240073;
const int HTTP_RESPONSE_ERROR = 240074;
const int HTTP_SERVER_ERROR = 240075;
const int DASHSCOPE_SERVER_ERROR = 240080;

// function call timeout
const int ENGINE_INIT_TIMEOUT = 240080;      // init engine timeout.
const int SET_PARAM_TIMEOUT = 240081;        // set parameters timeout.
const int SET_WUW_TIMEOUT = 240082;          // set wakeup word timeout.
const int SELECT_RECORDER_TIMEOUT = 240083;  // select recorder timeout.
const int STOP_TIMEOUT = 240084;             // stop dialog timeout.
const int ASR_ENGINE_STOP_TIMEOUT = 240085;  // asr engine stop timeout.
const int UNLOAD_DYNAMIC_WUW_TIMEOUT =
    240086;  // unload dynamic wakeup word timeout.
const int ADD_DYNAMIC_WUW_TIMEOUT = 240087;  // add dynamic wakeup word timeout.
const int HANDLE_API_TIMEOUT = 240088;
const int CHECK_ASSET_TIMEOUT = 240089;  // check asset bin file timeout
// network timeout
const int UPDATE_CONTEXT_TIMEOUT = 240090;  // update context timeout.
const int CONNECTION_TIMEOUT = 240091;      // connect to server timeout.
const int PARTIAL_ASR_TIMEOUT = 240092;     // get partial asr result timeout.
const int ASR_TIMEOUT = 240093;             // get final asr result timeout.
const int DIALOG_TIMEOUT = 240094;          // get dialog result timeout.
const int WWV_TIMEOUT = 240095;  // get wake word verification result timeout.
// legacy
const int WAIT_TIMEOUT = 240100;  // legacy definition.

#ifdef NUI_VIDEO_MANAGER
const int SELECT_CAMERA_TIMEOUT = 240101;
const int SELECT_CAMERA_ERROR = 240102;
#endif
#ifdef NUI_SENSOR_MANAGER
const int SELECT_SENSOR_TIMEOUT = 240103;
const int SELECT_SENSOR_ERROR = 240104;
#endif
#ifdef NUI_INCLUDE_VPR
const int VPR_ARGS_INVALID = 240110;
const int VPR_REG_TIMEOUT = 240111;
const int VPR_STATE_INVALID = 240112;
const int VPR_UPDATE_TIMEOUT = 240113;
const int VPR_DELETE_TIMEOUT = 240114;
const int VPR_CANCEL_TIMEOUT = 240115;
#endif

const int RING_BUF_WRITE_FAIL = 240120;
const int AUDIO_PROCESS_THREAD_BLOCK = 240121;
const int PERFORMANCE_FILEOVER = 240130;

const int RESAMPLE_ERR = 240140;
const int FILE_TRANS_TASK_LIMIT = 240150;
const int FILE_TRANS_ENCODER_FAIL = 240151;

const int THREAD_CREATE_FAILD = 240200;

// event tracker error
const int EVENT_TRACKER_FAILED = 240300;
const int EVENT_TRACKER_PARAM_INVALID = 240301;

const int SERVER_ERR_SILENT_SPEECH = 40010007;

}  // namespace nuisdk

#endif  // NUI_CODE_H_
