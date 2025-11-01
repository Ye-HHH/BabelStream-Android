/**
 * @file flowingttssdk.h
 * @author 挪麦 (lengjiayi.ljy@alibaba-inc.com)
 * @copyright Copyright (c) 2024 Alibaba
 * @date 2024-04-10
 * @brief :流式语音合成接口
 */
#ifndef COMMON_INCLUDE_STREAMINPUTTTSSDK_H_
#define COMMON_INCLUDE_STREAMINPUTTTSSDK_H_

#include "nui_code.h"

enum StreamInputTtsEvent {
  STREAM_INPUT_TTS_EVENT_SYNTHESIS_STARTED = 0,
  STREAM_INPUT_TTS_EVENT_SENTENCE_BEGIN = 1,
  STREAM_INPUT_TTS_EVENT_SENTENCE_SYNTHESIS = 2,
  STREAM_INPUT_TTS_EVENT_SENTENCE_END = 3,
  STREAM_INPUT_TTS_EVENT_SYNTHESIS_COMPLETE = 4,
  STREAM_INPUT_TTS_EVENT_TASK_FAILED = 5,
};

typedef void (*FuncNuiStreamInputTtsListenerOnEvent)(
    void *user_data, char *taskid, char *session_id, StreamInputTtsEvent event,
    int code, char *err_message, int err_message_length, char *timestamp,
    int timestamp_length, char *all_response, int all_response_length);
typedef void (*FuncNuiStreamInputTtsListenerOnData)(void *user_data,
                                                    char *taskid,
                                                    char *session_id,
                                                    char *buffer, int len);
typedef void (*FuncStreamInputTtsLogTrackListenerOnMessage)(
    void *user_data, PRODUCT_API_NAMESPACE::NuiSdkLogLevel level,
    const char *log);

struct StreamInputTtsListener {
  FuncNuiStreamInputTtsListenerOnEvent stream_input_tts_event_callback;
  FuncNuiStreamInputTtsListenerOnData stream_input_tts_user_data_callback;
  FuncStreamInputTtsLogTrackListenerOnMessage
      stream_input_tts_log_track_callback;
  void *user_data;
  StreamInputTtsListener() {
    stream_input_tts_event_callback = nullptr;
    stream_input_tts_user_data_callback = nullptr;
    stream_input_tts_log_track_callback = nullptr;
    user_data = nullptr;
  }
};

class StreamInputTtsSdk {
 public:
  StreamInputTtsSdk();
  ~StreamInputTtsSdk();
  int start(const char *ticket, const char *parameters, const char *session_id,
            StreamInputTtsListener *listener, int log_level, bool save_log,
            const char *single_round_text = nullptr);
  int play(const char *ticket, const char *parameters, const char *text,
           const char *session_id, StreamInputTtsListener *listener,
           int log_level, bool save_log);
  int send(const char *text);
  int stop();
  int async_stop();
  int cancel();
  // int sendPing();

 private:
  int set_request(const char *parameters, const char *appkey, const char *token,
                  const char *url, int complete_waiting_ms,
                  const char *session_id, const char *device_id,
                  const char *single_round_text);
#ifdef NUI_INCLUDE_DASHSCOPE
  int set_dashscope_request(const char *parameters, const char *apikey,
                            const char *url, int complete_waiting_ms,
                            const char *session_id, const char *device_id,
                            const char *single_round_text);
  void get_service_protocol_from_url(const char *ticket);
#endif /* NUI_INCLUDE_DASHSCOPE */

  // std::mutex mtx;
  void *_request;
  void *listener_;
  int service_protocol_;
  void *easy_et_handler_;
};

#endif  // COMMON_INCLUDE_STREAMINPUTTTSSDK_H_