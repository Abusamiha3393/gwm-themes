/*
 * ثيم «هافال» من تصميم صاحب التطبيق: عدادان معدنيان في دائرتي الخلفية،
 * ونافذة معلومات في الوسط. الإحداثيات على لوحة 1920×720.
 * الصيغة نفسها لأي ثيم صور: خلفية، عدادات بإبر، صور، ونصوص مربوطة بالبيانات.
 * لكل عنصر id ثابت واسم عربي يظهر في محرر الثيم، و hidden: true يجعله مخفيًا حتى يُظهره السائق.
 */
window.SAHRA_LAYOUT = {
  name: 'هافال',
  canvas: { w: 1920, h: 720 },
  background: 'bg_gray.png',
  variants: { gray: 'bg_gray.png', blue: 'bg_blue.png' },
  variantNames: { gray: 'رمادي معدني', blue: 'أزرق مضيء' },
  gauges: [
    { id: 'speedGauge', name: 'عداد السرعة', value: 'speed', dial: 'spd.png', needle: 'needle_spd.png', cx: 335, cy: 385, size: 500, min: 0, max: 220, a0: -135, a1: 135 },
    { id: 'rpmGauge', name: 'عداد الدوران', value: 'rpm1000', dial: 'rpm.png', needle: 'needle_rpm.png', cx: 1585, cy: 385, size: 500, min: 0, max: 8, a0: -135, a1: 135 }
  ],
  images: [
    { id: 'car', name: 'صورة السيارة', file: 'car.png', x: 960, y: 455, w: 230, anchor: 'center', opacity: 0.9 }
  ],
  texts: [
    { id: 'gear', name: 'الغيار', value: 'gearLabel', x: 960, y: 205, size: 64, font: 'digits', color: '#FFFFFF', accent: true },
    { id: 'speed', name: 'السرعة بالأرقام', value: 'speed', x: 960, y: 290, size: 96, font: 'digits', color: '#FFFFFF', unit: 'كم/س', unitSize: 22 },
    { id: 'tireFL', name: 'إطار أمامي يسار', value: 'tire0', x: 790, y: 400, size: 30, font: 'digits', color: '#FFFFFF' },
    { id: 'tireFR', name: 'إطار أمامي يمين', value: 'tire1', x: 1130, y: 400, size: 30, font: 'digits', color: '#FFFFFF' },
    { id: 'tireRL', name: 'إطار خلفي يسار', value: 'tire2', x: 790, y: 530, size: 30, font: 'digits', color: '#FFFFFF' },
    { id: 'tireRR', name: 'إطار خلفي يمين', value: 'tire3', x: 1130, y: 530, size: 30, font: 'digits', color: '#FFFFFF' },
    { id: 'tireTempFL', name: 'حرارة إطار أمامي يسار', value: 'tireTemp0', x: 790, y: 435, size: 20, font: 'digits', color: '#9AA3AD', unit: '°', hidden: true },
    { id: 'tireTempFR', name: 'حرارة إطار أمامي يمين', value: 'tireTemp1', x: 1130, y: 435, size: 20, font: 'digits', color: '#9AA3AD', unit: '°', hidden: true },
    { id: 'tireTempRL', name: 'حرارة إطار خلفي يسار', value: 'tireTemp2', x: 790, y: 565, size: 20, font: 'digits', color: '#9AA3AD', unit: '°', hidden: true },
    { id: 'tireTempRR', name: 'حرارة إطار خلفي يمين', value: 'tireTemp3', x: 1130, y: 565, size: 20, font: 'digits', color: '#9AA3AD', unit: '°', hidden: true },
    { id: 'fuel', name: 'الوقود', value: 'fuel', x: 745, y: 600, size: 34, font: 'digits', color: '#FFFFFF', unit: '%', icon: 'icon4.png', warnBelow: 15, alarmBelow: 8 },
    { id: 'range', name: 'المدى المتبقي', value: 'range', x: 905, y: 600, size: 34, font: 'digits', color: '#FFFFFF', unit: 'كم' },
    { id: 'coolant', name: 'حرارة المحرك', value: 'coolant', x: 1055, y: 600, size: 34, font: 'digits', color: '#FFFFFF', unit: '°', icon: 'icon5.png', warnAbove: 105, alarmAbove: 114 },
    { id: 'outside', name: 'الحرارة الخارجية', value: 'outsideTemp', x: 1195, y: 600, size: 34, font: 'digits', color: '#FFFFFF', unit: '°', label: 'الخارج' },
    { id: 'inside', name: 'حرارة المقصورة', value: 'insideTemp', x: 1195, y: 545, size: 28, font: 'digits', color: '#C9D1D9', unit: '°', label: 'الداخل', hidden: true },
    { id: 'odometer', name: 'العداد الكلي', value: 'odometer', x: 960, y: 655, size: 22, font: 'digits', color: '#9AA3AD', unit: 'كم' },
    { id: 'trip', name: 'عداد الرحلة', value: 'trip', x: 760, y: 655, size: 22, font: 'digits', color: '#9AA3AD', unit: 'كم', label: 'رحلة', hidden: true },
    { id: 'journey', name: 'مسافة الرحلة الحالية', value: 'journey', x: 760, y: 690, size: 22, font: 'digits', color: '#9AA3AD', unit: 'كم', digits: 1, hidden: true },
    { id: 'avg', name: 'متوسط الاستهلاك', value: 'avgConsumption', x: 1160, y: 655, size: 22, font: 'digits', color: '#9AA3AD', unit: 'ل/100', digits: 1, hidden: true },
    { id: 'volts', name: 'جهد البطارية', value: 'batteryVoltage', x: 1760, y: 650, size: 24, font: 'digits', color: '#C9D1D9', unit: 'V', digits: 1, hidden: true },
    { id: 'battery', name: 'شحن البطارية', value: 'batteryLevel', x: 1620, y: 650, size: 24, font: 'digits', color: '#C9D1D9', unit: '%', hidden: true },
    { id: 'clock', name: 'الساعة', value: 'clock', x: 1700, y: 690, size: 26, font: 'digits', color: '#C9D1D9' }
  ]
};
