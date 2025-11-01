#ifndef ANDROID_INCLUDE_NUISDK_H
#define ANDROID_INCLUDE_NUISDK_H
#include "nui_code.h"
namespace PRODUCT_API_NAMESPACE {

enum NuiCallbackEvent {
  EVENT_VAD_START,
  EVENT_VAD_TIMEOUT,
  EVENT_VAD_END,
  EVENT_WUW,
  EVENT_WUW_TRUSTED,
  EVENT_WUW_CONFIRMED,
  EVENT_WUW_REJECTED,
  EVENT_WUW_END,
  EVENT_ASR_PARTIAL_RESULT,
  EVENT_ASR_RESULT,
  EVENT_ASR_ERROR,  // 10
  EVENT_DIALOG_ERROR,
  EVENT_ONESHOT_TIMEOUT,
  EVENT_DIALOG_RESULT,
  EVENT_WUW_HINT,
  EVENT_VPR_RESULT,
  EVENT_TEXT2ACTION_DIALOG_RESULT,
  EVENT_TEXT2ACTION_ERROR,
  EVENT_ATTR_RESULT,
  EVENT_MIC_ERROR,
  EVENT_DIALOG_EX,  // 20
  EVENT_WUW_ERROR,
  EVENT_BEFORE_CONNECTION,
  EVENT_SENTENCE_START,
  EVENT_SENTENCE_END,
  EVENT_SENTENCE_SEMANTICS,
  EVENT_RESULT_TRANSLATED,  // 26
  EVENT_TRANSCRIBER_COMPLETE,
  EVENT_FILE_TRANS_CONNECTED,
  EVENT_FILE_TRANS_UPLOADED,
  EVENT_FILE_TRANS_RESULT,  // 30
  EVENT_FILE_TRANS_UPLOAD_PROGRESS,
  EVENT_TRANSCRIBER_STARTED,
  EVENT_ASR_STARTED,
  EVENT_FILE_TRANS_QUERY_RESULT,
  EVENT_WUW_START,
  EVENT_WUW_DATA,
};

enum NuiVadMode {
  MODE_VAD,
  MODE_P2T,
  MODE_KWS,
  MODE_PARALLEL,
  MODE_KWS2PARALLEL,
  MODE_AUTO_CONTINUAL,
  MODE_KWS_CONTINUAL,
  MODE_KWS2TALK,
  MODE_ONLY_KWS = 8,
};

enum ServiceType {
  SERVICE_TYPE_NONE = -1,
  SERVICE_TYPE_ASR = 0,  // SpeechRecognition
  SERVICE_TYPE_TIANGONG_ASSISTANT = 1,
  SERVICE_TYPE_DIALOG_ASSISTANT = 2,   // DialogAssistant
  SERVICE_TYPE_VIRTUAL_ASSISTANT = 3,  // TiangongV4
  SERVICE_TYPE_SPEECH_TRANSCRIBER = 4  // SpeechTranscriber
};

enum NuiSdkVprEvent {
  EVENT_VPR_NONE,
  EVENT_VPR_REGISTER_START,
  EVENT_VPR_REGISTER_DONE,
  EVENT_VPR_REGISTER_FAILED,
  EVENT_VPR_UPDATE_START,
  EVENT_VPR_UPDATE_DONE,
  EVENT_VPR_UPDATE_FAIL,
  EVENT_VPR_DELETE_DONE,
  EVENT_VPR_DELETE_FAIL
};

// enum NuiTtsParam {
//   PARAM_PITCH_LEVEL,
//   PARAM_SPEED_LEVEL,
//   PARAM_VOLUME,
//   PARAM_FONT_NAME,
//   PARAM_MODE_TYPE,
//   PARAM_TTS_MAX,
// };

typedef void (*FuncDialogListenerOnEvent)(void *user_data,
                                          NuiCallbackEvent event, long dialog,
                                          const char *wuw,
                                          const char *asr_result, bool finish,
                                          int code, const char *all_response);
typedef void (*FuncFileTransListenerOnEvent)(void *user_data,
                                             NuiCallbackEvent event,
                                             const char *result,
                                             const char *task_id, bool finish,
                                             int code);
typedef int (*FuncDialogUserProvideData)(void *user_data, char *buffer,
                                         int len);
typedef void (*FuncDialogAudioStateChange)(void *user_data,
                                           NuiAudioState state);
typedef void (*FuncDialogAudioExtraEvent)(void *user_data, void *extra_data,
                                          NuiAudioExtraEvent event);

typedef void (*FuncVprListenerOnEvent)(void *user_data, NuiSdkVprEvent event);

typedef void (*FuncAssistListenerOnEvent)(void *user_data,
                                          NuiCallbackEvent event, char *info,
                                          int info_len, char *buffer, int len);

typedef void (*FuncLogTrackOnMessage)(void *user_data, NuiSdkLogLevel level,
                                      const char *log);

struct NuiSdkListener {
  NuiSdkListener()
      : event_callback(nullptr),
        file_trans_event_callback(nullptr),
        file_trans_log_track_callback(nullptr),
        user_data_callback(nullptr),
        audio_state_changed_callback(nullptr),
        audio_extra_event_callback(nullptr),
        vpr_event_callback(nullptr),
        assist_data_callback(nullptr),
        log_track_callback(nullptr),
        user_data(nullptr) {}

  FuncFileTransListenerOnEvent file_trans_event_callback;
  FuncLogTrackOnMessage file_trans_log_track_callback;
  FuncDialogListenerOnEvent event_callback;
  FuncDialogUserProvideData user_data_callback;
  FuncDialogAudioStateChange audio_state_changed_callback;
  FuncDialogAudioExtraEvent audio_extra_event_callback;
  FuncVprListenerOnEvent vpr_event_callback;
  FuncAssistListenerOnEvent assist_data_callback;
  FuncLogTrackOnMessage log_track_callback;
  void *user_data;
};

class NuiSdk {
 public:
  NuiSdk();
  ~NuiSdk();

  /**
   *  @brief nui initialize function.
   *  @param parameters init parameters, details accord to api doc.
   *  @param listener nuisdk event listener.
   *  @param async_listener set a async listener on async mode to get function
   * return result.
   *  @param level log level.
   *  @param save_log if save log to debug, if yes please set debug_path by
   * parameters.
   *  @return result code.
   */
  NuiResultCode nui_initialize(const char *parameters,
                               const NuiSdkListener *listener,
                               const NuiAsyncCallback *async_listener = nullptr,
                               NuiSdkLogLevel level = LOG_LEVEL_VERBOSE,
                               bool save_log = false);

  /**
   * @brief nui release function.
   * @param async_listener set a async listener on async mode to get function
   * return result.
   * @return result code.
   */
  NuiResultCode nui_release(const NuiAsyncCallback *async_listener = nullptr);

  /**
   * @brief nui start function， start a new dialog.
   * @param vad_mode vad mode, refer to NuiVadMode.
   * @param dialog_params dialog parameters in json format. Details accord to
   * api doc.
   * @param listener set a async listener on async mode to get function return
   * result.
   * @return result code.
   */
  NuiResultCode nui_dialog_start(NuiVadMode vad_mode, const char *dialog_params,
                                 const NuiAsyncCallback *listener = nullptr);

  /**
   * @brief nui dialog cancel function， cancel a running dialog.
   * @param force if true, dialog will canceled without any event callback,
   *              if false, dialog event with reported.
   * @param listener set a async listener on async mode to get function return
   * result.
   * @return result code.
   */
  NuiResultCode nui_dialog_cancel(bool force,
                                  const NuiAsyncCallback *listener = nullptr);

  /**
   * @brief nui dialog resume function， resume dialog after finish hint.
   * @return result code.
   */
  NuiResultCode nui_dialog_resume();

  /**
   * @brief request NLU/DM result by text input.
   * @param text text for nlu.
   * @param context the context in json for NLU server.
   * @param is_new_dialog a new dialog or multi round.
   * @param dialog_params dialog parameters in json format. Details accord to
   * api doc.
   * @param listener set a async listener on async mode to get function return
   * result.
   * @return result code.
   */
  NuiResultCode nui_dialog_text2action(const char *text, const char *context,
                                       bool is_new_dialog,
                                       const char *dialog_params,
                                       const NuiAsyncCallback *listener);

  /**
   * @brief cancel text to action call.
   * @return result code.
   */
  NuiResultCode nui_dialog_text2action_cancel();

  /**
   * @brief nui set parameter, key-value format.
   * @param para parameter key string.
   * @param value parameter value string.
   * @param listener set a async listener on async mode to get function return
   * result.
   * @return result code.
   */
  NuiResultCode nui_set_param(const char *para, const char *value,
                              const NuiAsyncCallback *listener = nullptr);

  /**
   * @brief nui set parameters, json format.
   * @param params parameters in json format string.
   * @param listener set a async listener on async mode to get function return
   * result.
   * @return result code.
   */
  NuiResultCode nui_set_params(const char *params,
                               const NuiAsyncCallback *listener = nullptr);

  /*
   * @brief get version. If module not set, SDK version will return.
   * @param module can be set to:
   *   kws_engine for wakeup engine version.
   *   kws_model for wakup model version.
   */
  const char *nui_get_version(const char *module = nullptr);

  /**
   * @brief check bin file, like wakup model.
   * @param file the path of bin file.
   * @return result code.
   */
  int nui_check_asset(const char *file);

  /**
   * @brief nui add parameter into init parameters.
   * @param params init parameters, input by nui_initialize.
   * @param key parameter key string.
   * @param val parameter value string.
   * @return new parameters.
   */
  const char *nui_add_params_in_ticket(const char *params, const char *key,
                                       const char *val);

  /**
   * @brief 开始识别
   * @param params 设置识别参数
   * @param task_id SDK生成的请求ID,长度为36
   * @param listener 异步回调，设置nullptr采用同步方式调用
   * @return 参见错误码
   */
  int nui_file_trans_start(const char *params, char *task_id,
                           const NuiAsyncCallback *listener = nullptr);

  /**
   * @brief 查询结果
   * @param listener 异步回调，设置nullptr采用同步方式调用
   * @return 参见错误码
   */
  int nui_file_trans_query(const char *task_id,
                           const NuiAsyncCallback *listener = nullptr);
  /**
   * @brief 结束识别
   * @param listener 异步回调，设置nullptr采用同步方式调用
   * @return 参见错误码
   */
  int nui_file_trans_cancel(const char *task_id,
                            const NuiAsyncCallback *listener = nullptr);

  /**
   * @brief 推送音频
   * @param data 存储音频的buffer地址
   * @param len_in_byte 音频buffer长度
   * @param first_pack 是否开始录音对第一帧，仅在AEC时使用，可忽略
   * @return 参见错误码
   */
  int nui_update_audio_data(const char *data, int len_in_byte,
                            bool first_pack = false);

  NuiResultCode nui_vpr_register_user(
      const char *service_id, const char *group_id, const char *user_id,
      const NuiAsyncCallback *listener = nullptr);
  NuiResultCode nui_vpr_update_user(const char *service_id,
                                    const char *group_id, const char *user_id,
                                    const NuiAsyncCallback *listener = nullptr);
  NuiResultCode nui_vpr_delete_user(const char *service_id,
                                    const char *group_id, const char *user_id,
                                    const NuiAsyncCallback *listener = nullptr);
  NuiResultCode nui_vpr_enable(const NuiAsyncCallback *listener = nullptr);
  NuiResultCode nui_vpr_disable(const NuiAsyncCallback *listener = nullptr);
  NuiResultCode nui_vpr_register_cancel(
      const NuiAsyncCallback *listener = nullptr);

 private:
  void *impl_ptr;
};

}  // namespace PRODUCT_API_NAMESPACE

#endif
