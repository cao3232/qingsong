<template>
  <main class="pdf-reader-page">
    <header class="reader-header">
      <button class="back-button" type="button" title="返回" @click="goBack">‹</button>
      <div class="reader-title"><strong>{{ fileName || 'PDF 阅读器' }}</strong><span v-if="fileName">{{ progressText
      }}</span></div>
      <button v-if="file" class="settings-button" type="button" @click="toggleOutline">目录</button>
      <button class="open-button" type="button" @click="selectFile">打开 PDF</button>
      <button class="settings-button" type="button" @click="showVoiceSettings = !showVoiceSettings">语音设置</button>
      <input ref="fileInputRef" class="hidden-file" type="file" accept="application/pdf,.pdf"
        @change="handleFileInput" />
    </header>

    <section v-if="showVoiceSettings" class="voice-settings">
      <label>
        <span>预置音色</span>
        <select :value="voice" :disabled="Boolean(voiceDesign?.trim()) || Boolean(cloneSample)"
          @change="setVoice($event.target.value)">
          <option v-for="option in TTS_VOICES" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </label>
      <label class="design-field">
        <span>音色设计</span>
        <input :value="voiceDesign" placeholder="例如：温柔、舒缓、适合长时间阅读" @change="setVoiceDesign($event.target.value)" />
      </label>
      <label class="api-key-field" :title="'API Key 按浏览器来源分别保存；局域网/其他浏览器访问需各自配置一次'">
        <span>TTS Key</span>
        <input :value="apiKey" placeholder="粘贴 MiMo API Key（内置 Key 可能失效）" @change="saveApiKey($event.target.value)" />
      </label>
      <span class="voice-hint">{{ cloneSample ? `当前使用克隆音色：${cloneSample.name}` : voiceDesign?.trim() ? '当前使用音色设计' :
        '当前使用预置音色' }}</span>
    </section>

    <section v-if="!file" class="upload-state" :class="{ dragging: isDragging }" @click="selectFile"
      @dragover.prevent="isDragging = true" @dragleave.prevent="isDragging = false" @drop.prevent="handleDrop">
      <div class="upload-icon">PDF</div>
      <h1>开始阅读一本 PDF</h1>
      <p>选择文件或将 PDF 拖到这里，加载后即可朗读</p>
      <button type="button" @click.stop="selectFile">选择 PDF 文件</button>
      <small v-if="errorMessage">{{ errorMessage }}</small>
    </section>

    <template v-else>
      <div class="reader-body">
        <aside v-if="showOutline" class="outline-panel">
          <div class="outline-header">目录</div>
          <div v-if="flatOutline.length" class="outline-list">
            <button v-for="(entry, index) in flatOutline" :key="`${entry.title}-${entry.page}-${entry.depth}-${index}`"
              type="button" class="outline-item" :class="`depth-${Math.min(entry.depth, 4)}`"
              @click="jumpToOutline(entry)">
              <span class="outline-title" :title="entry.title">{{ entry.title }}</span>
              <span class="outline-page">{{ entry.page }}</span>
            </button>
          </div>
          <div v-else class="outline-empty">该 PDF 没有目录</div>
        </aside>
        <div class="reader-main">
          <PDFReaderViewer :file="file" :page-number="pageNumber" :scale="scale" @page-count="setPageCount"
            @page-text="setPageText" @page-change="setPage" @scale-change="setScale" @outline="setOutline"
            @text-done="onTextDone" @load-error="handleLoadError" @render-error="errorMessage = 'PDF 页面渲染失败'" />
          <footer class="reader-player">
            <button class="play-button" type="button" :title="isPlaying ? '停止朗读' : '开始朗读'"
              @click="isPlaying ? stop() : play()">{{ isPlaying ? '■' : '▶' }}</button>
            <div class="player-copy"><strong>{{ isPlaying ? `正在朗读第 ${pageNumber} 页` : '准备朗读' }}</strong><span>{{
              errorMessage || '按页连续播放文字内容' }}</span></div>
            <label class="rate-control">语速<select :value="playbackRate" @change="setPlaybackRate($event.target.value)">
                <option v-for="rate in TTS_PLAYBACK_RATES" :key="rate.value" :value="rate.value">{{ rate.label }}
                </option>
              </select></label>
            <button class="stop-button" type="button" title="停止" @click="stop">停止</button>
          </footer>
        </div>
      </div>
    </template>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { PDFReaderViewer } from '../components/index.js'
import { usePdfReaderPage } from '../composables/index.js'

const {
  apiKey, cloneSample, errorMessage, file, fileInputRef, fileName, flatOutline, goBack, handleDrop, handleFileInput,
  handleLoadError, isDragging, isPlaying, jumpToOutline, onTextDone, pageNumber, play, playbackRate,
  progressText, saveApiKey, selectFile, setPage, setPageCount, setPageText, setPlaybackRate, setScale, setVoice,
  setVoiceDesign, setOutline, showOutline, stop, toggleOutline, TTS_PLAYBACK_RATES,
  TTS_VOICES, voice, voiceDesign, scale
} = usePdfReaderPage()

const showVoiceSettings = ref(false)
</script>

<style scoped lang="scss">
.pdf-reader-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #eef2f6;
  color: #1e293b;
}

.reader-body {
  min-height: 0;
  flex: 1;
  display: flex;
  position: relative;
}

.reader-main {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.outline-panel {
  width: min(280px, 78vw);
  flex: none;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid #d8dee6;
  overflow-y: auto;
}

.outline-header {
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid #e2e8f0;
}

.outline-list {
  padding: 6px 0;
}

.outline-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: #334155;
  font-size: 13px;
}

.outline-item:hover {
  background: #f1f5f9;
}

.outline-item.depth-1 {
  padding-left: 26px;
}

.outline-item.depth-2 {
  padding-left: 38px;
}

.outline-item.depth-3 {
  padding-left: 50px;
}

.outline-item.depth-4 {
  padding-left: 62px;
}

.outline-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-page {
  flex: none;
  color: #94a3b8;
  font-size: 12px;
}

.outline-empty {
  padding: 20px 14px;
  color: #94a3b8;
  font-size: 13px;
}

.reader-header {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 54px;
  padding: 8px 18px;
  background: #17212b;
  color: #fff;
}

.back-button {
  border: 0;
  background: transparent;
  display: inline-block;
  padding: 0 10px;
  color: #fff;
  font-size: 30px;
  line-height: 1;
  cursor: pointer;
}

.reader-title {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}

.reader-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.reader-title span,
.player-copy span {
  color: #94a3b8;
  font-size: 12px;
}

.open-button,
.settings-button,
.upload-state button,
.stop-button {
  border: 1px solid #475569;
  padding: 7px 12px;
  background: #243443;
  color: #fff;
  cursor: pointer;
}

.settings-button {
  background: transparent;
}

.hidden-file {
  display: none;
}

.voice-settings {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 18px;
  background: #243443;
  color: #e2e8f0;
  border-bottom: 1px solid #475569;
  font-size: 12px;
}

.voice-settings label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.voice-settings select,
.voice-settings input {
  min-height: 30px;
  border: 1px solid #64748b;
  padding: 5px 8px;
  background: #17212b;
  color: #fff;
}

.voice-settings select:disabled {
  opacity: .55;
}

.design-field input {
  width: min(320px, 35vw);
}

.api-key-field input {
  width: min(320px, 35vw);
}

.voice-hint {
  color: #94a3b8;
}

.upload-state {
  width: min(560px, calc(100% - 32px));
  margin: auto;
  padding: 56px 24px;
  border: 1px dashed #94a3b8;
  background: #fff;
  text-align: center;
  cursor: pointer;
}

.upload-state.dragging {
  border-color: #0f766e;
  background: #f0fdfa;
}

.upload-icon {
  width: 64px;
  margin: 0 auto 18px;
  padding: 16px 0;
  border: 2px solid #0f766e;
  color: #0f766e;
  font-weight: 700;
}

.upload-state h1 {
  margin-bottom: 8px;
  font-size: 22px;
}

.upload-state p {
  margin-bottom: 22px;
  color: #64748b;
}

.upload-state button {
  background: #0f766e;
  border-color: #0f766e;
}

.upload-state small {
  display: block;
  margin-top: 16px;
  color: #b42318;
}

.reader-player {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 72px;
  padding: 10px 18px;
  background: #17212b;
  color: #fff;
}

.play-button {
  width: 42px;
  height: 42px;
  border: 0;
  background: #0f766e;
  color: #fff;
  cursor: pointer;
  font-size: 18px;
}

.player-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.player-copy strong {
  font-size: 13px;
}

.rate-control {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #cbd5e1;
  font-size: 12px;
}

.rate-control select {
  border: 1px solid #475569;
  padding: 6px;
  background: #243443;
  color: #fff;
}

@media (max-width: 640px) {
  .reader-header {
    padding: 8px 10px;
  }

  .reader-player {
    gap: 9px;
    padding: 8px 10px;
  }

  .voice-settings {
    align-items: stretch;
    flex-direction: column;
    padding: 10px;
  }

  .design-field input {
    width: 100%;
  }

  .stop-button {
    display: none;
  }

  .rate-control {
    flex-direction: column;
    gap: 2px;
  }

  .outline-panel {
    position: absolute;
    inset: 0 auto 0 0;
    z-index: 3;
  }
}
</style>
