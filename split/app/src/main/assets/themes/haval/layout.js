/*
 * ثيم «هافال» من تصميم صاحب التطبيق: عدادان معدنيان في دائرتي الخلفية،
 * ونافذة معلومات في الوسط. الإحداثيات على لوحة 1920×720.
 * الصيغة نفسها لأي ثيم صور: خلفية، عدادات بإبر، صور، ونصوص مربوطة بالبيانات.
 */
window.SAHRA_LAYOUT = {
  name: 'هافال',
  canvas: { w: 1920, h: 720 },
  background: 'bg_gray.png',
  variants: { gray: 'bg_gray.png', blue: 'bg_blue.png' },
  gauges: [
    { value: 'speed', dial: 'spd.png', needle: 'needle_spd.png', cx: 335, cy: 385, size: 500, min: 0, max: 220, a0: -135, a1: 135 },
    { value: 'rpm1000', dial: 'rpm.png', needle: 'needle_rpm.png', cx: 1585, cy: 385, size: 500, min: 0, max: 8, a0: -135, a1: 135 }
  ],
  images: [
    { file: 'car.png', x: 960, y: 455, w: 230, anchor: 'center', opacity: 0.9 }
  ],
  texts: [
    { value: 'gear', x: 960, y: 205, size: 64, font: 'digits', color: '#FFFFFF', accent: true },
    { value: 'speed', x: 960, y: 290, size: 96, font: 'digits', color: '#FFFFFF', unit: 'كم/س', unitSize: 22 },
    { value: 'fuel', x: 745, y: 600, size: 34, font: 'digits', color: '#FFFFFF', unit: '%', icon: 'icon4.png', warnBelow: 15, alarmBelow: 8 },
    { value: 'range', x: 905, y: 600, size: 34, font: 'digits', color: '#FFFFFF', unit: 'كم' },
    { value: 'coolant', x: 1055, y: 600, size: 34, font: 'digits', color: '#FFFFFF', unit: '°', icon: 'icon5.png', warnAbove: 105, alarmAbove: 114 },
    { value: 'outsideTemp', x: 1195, y: 600, size: 34, font: 'digits', color: '#FFFFFF', unit: '°', label: 'الخارج' },
    { value: 'odometer', x: 960, y: 655, size: 22, font: 'digits', color: '#9AA3AD', unit: 'كم' },
    { value: 'clock', x: 1700, y: 690, size: 26, font: 'digits', color: '#C9D1D9' }
  ]
};
