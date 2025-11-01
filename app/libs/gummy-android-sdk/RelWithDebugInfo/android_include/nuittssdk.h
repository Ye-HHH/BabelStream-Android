/**
 * @file nuittssdk.h
 * @author 追音 (weisheng.hws@alibaba-inc.com)
 * @copyright Copyright (c) 2021 Alibaba
 * @date 2021-03-04
 * @brief :多实例语音合成接口
 */
#ifndef COMMON_INCLUDE_NUITTSSDK_H_
#define COMMON_INCLUDE_NUITTSSDK_H_

#include "nui_code.h"
namespace PRODUCT_API_NAMESPACE {

enum NuiSdkTtsEvent {
  TTS_EVENT_START = 0,
  TTS_EVENT_END = 1,
  TTS_EVENT_CANCEL = 2,
  TTS_EVENT_PAUSE = 3,
  TTS_EVENT_RESUME = 4,
  TTS_EVENT_ERROR = 5,
  TTS_EVENT_CACEH_START = 6,
  TTS_EVENT_CACEH_END = 7,
  TTS_EVENT_CACEH_CANCEL = 8,
  TTS_EVENT_CACEH_DELETE = 9,
  TTS_EVENT_CACEH_ERROR = 10,
  TTS_EVENT_FONT_EVENT_START = 11,
  TTS_EVENT_FONT_DOWNLOAD = 12,
  TTS_EVENT_FONT_END = 13,
  TTS_EVENT_FONT_PAUSE = 14,
  TTS_EVENT_FONT_RESUME = 15,
  TTS_EVENT_FONT_CANCEL = 16,
  TTS_EVENT_FONT_ERROR = 17
};

typedef void (*FuncNuiTtsListenerOnEvent)(void *user_data, NuiSdkTtsEvent event,
                                          char *taskid, int code);
typedef void (*FuncNuiTtsUserProvideData)(void *user_data, char *info,
                                          int info_len, char *buffer, int len,
                                          char *taskid);
typedef void (*FuncNuiTtsListenerFontProgress)(void *user_data,
                                               NuiSdkTtsEvent event,
                                               char *res_id, int speed,
                                               int percent, int code);
typedef void (*FuncNuiTtsUserProvideVolume)(void *user_data, int volume,
                                            char *taskid);

typedef void (*FuncNuiTtsLogTrackOnMessage)(void *user_data,
                                            NuiSdkLogLevel level,
                                            const char *log);

struct NuiTtsSdkListener {
  FuncNuiTtsListenerOnEvent tts_event_callback;
  FuncNuiTtsUserProvideData tts_user_data_callback;
  FuncNuiTtsUserProvideVolume tts_user_volume_callback;
  FuncNuiTtsListenerFontProgress tts_font_progress_callback;
  FuncNuiTtsLogTrackOnMessage tts_log_track_callback;
  void *user_data;
};

class NuiTtsSdk {
 public:
  NuiTtsSdk();
  ~NuiTtsSdk();
  /**
   * @brief 初始化一路
   * @param parameters
   * 初始化参数，json格式，包含鉴权信息（如url/appkey等）以及工作路径
   * @param listener 回调函数，从SDK获取音频数据及事件
   * @param async_listener 异步回调函数，暂未开放，设为nullptr即可
   * @param level 日志等级
   * @param save_log 存储日志的开关
   * @return int :错误码
   */
  int nui_tts_initialize(const char *parameters,
                         const NuiTtsSdkListener *listener,
                         const NuiAsyncCallback *async_listener = nullptr,
                         NuiSdkLogLevel level = LOG_LEVEL_VERBOSE,
                         bool save_log = false);
  int nui_tts_release(const NuiAsyncCallback *async_listener = nullptr);
  int nui_tts_play(const char *priority, const char *taskid, const char *text,
                   const NuiAsyncCallback *listener);
  int nui_tts_cancel(const char *taskid,
                     const NuiAsyncCallback *listener = nullptr);
  int nui_tts_pause(const NuiAsyncCallback *listener = nullptr);
  int nui_tts_resume(const NuiAsyncCallback *listener = nullptr);
  int nui_tts_set_param(const char *param, const char *value,
                        const NuiAsyncCallback *listener = nullptr);
  const char *nui_tts_get_param(const char *param,
                                const NuiAsyncCallback *listener = nullptr);
  int nui_tts_get_num_of_chars(const char *text);
  const char *nui_tts_add_params_in_ticket(const char *ticket, const char *key,
                                           const char *val);

 private:
  void *impl_ptr_;
  void *listener_;
};

}  // namespace PRODUCT_API_NAMESPACE

#endif  // COMMON_INCLUDE_NUITTSSDK_H_
