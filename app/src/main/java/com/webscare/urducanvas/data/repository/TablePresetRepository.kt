package com.webscare.urducanvas.data.repository

import android.graphics.Color
import com.webscare.urducanvas.common.canvas.enums.TableBorderMode
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.enums.VAlign
import com.webscare.urducanvas.common.canvas.model.TableCell
import com.webscare.urducanvas.common.canvas.model.TableData
import com.webscare.urducanvas.common.canvas.model.TableTextStyle

data class TablePresetStyle(
    val id: String,
    val name: String,
    val category: String,
    val headerBgColor: Int,
    val headerTextColor: Int,
    val row1BgColor: Int,
    val row2BgColor: Int,
    val bodyTextColor: Int,
    val borderColor: Int,
    val borderWidth: Float = 1f,
    val borderMode: TableBorderMode = TableBorderMode.ALL,
    val rows: Int = 5,
    val cols: Int = 3,
    val hasHeader: Boolean = true,
    val hasFooter: Boolean = false,
    val hasHeaderCol: Boolean = false,
    val cornerRadius: Float = 0f,
    val paddingH: Float = 10f,
    val paddingV: Float = 8f,
    val footerBgColor: Int? = null,
    val footerTextColor: Int? = null,
    val headerColBgColor: Int? = null,
    val headerColTextColor: Int? = null,
    val headerBold: Boolean = true,
    val bodyBold: Boolean = false,
    val footerBold: Boolean = true,
    val prefillHeaders: List<String>? = null,
    val prefillRows: List<List<String>>? = null,
    val prefillFooter: List<String>? = null,
    val isRTL: Boolean = true
)

object TablePresetRepository {

    val categories = listOf(
        "Price List", "Schedule", "Data Table", "Comparison", "Contacts",
        "Tracking", "Islamic", "Card Style", "Business", "Classic"
    )

    private data class Palette(
        val h: String,
        val ht: String,
        val r1: String,
        val r2: String,
        val bt: String,
        val b: String
    )

    private val palettes = listOf(
        // Emerald / Forest Greens
        Palette("#005D28", "#FFFFFF", "#FFFFFF", "#E4F3E9", "#1F2937", "#005D28"),
        Palette("#065F46", "#FFFFFF", "#FFFFFF", "#ECFDF5", "#1F2937", "#10B981"),
        Palette("#15803D", "#FFFFFF", "#FFFFFF", "#F0FDF4", "#1F2937", "#86EFAC"),
        Palette("#047857", "#FFFFFF", "#F0FDF4", "#DCFCE7", "#14532D", "#059669"),
        Palette("#166534", "#FFFFFF", "#FFFFFF", "#F7FEE7", "#365314", "#4ADE80"),

        // Royal / Navy Blues
        Palette("#1E3A8A", "#FFFFFF", "#FFFFFF", "#EFF6FF", "#1E293B", "#3B82F6"),
        Palette("#1E40AF", "#FFFFFF", "#FFFFFF", "#F0F9FF", "#0F172A", "#60A5FA"),
        Palette("#0369A1", "#FFFFFF", "#FFFFFF", "#E0F2FE", "#0C4A6E", "#38BDF8"),
        Palette("#0284C7", "#FFFFFF", "#F8FAFC", "#F1F5F9", "#334155", "#94A3B8"),
        Palette("#0C4A6E", "#FFFFFF", "#FFFFFF", "#ECFEFF", "#164E63", "#06B6D4"),

        // Deep Purples & Violets
        Palette("#581C87", "#FFFFFF", "#FFFFFF", "#FAF5FF", "#3B0764", "#A855F7"),
        Palette("#6B21A8", "#FFFFFF", "#FFFFFF", "#F5F3FF", "#2E1065", "#8B5CF6"),
        Palette("#7C3AED", "#FFFFFF", "#FFFFFF", "#EDE9FE", "#4C1D95", "#C4B5FD"),
        Palette("#4C1D95", "#FFFFFF", "#FDF4FF", "#FAE8FF", "#701A75", "#E879F9"),
        Palette("#4338CA", "#FFFFFF", "#FFFFFF", "#EEF2FF", "#312E81", "#818CF8"),

        // Crimson & Ruby Reds
        Palette("#991B1B", "#FFFFFF", "#FFFFFF", "#FEF2F2", "#450A0A", "#EF4444"),
        Palette("#B91C1C", "#FFFFFF", "#FFFFFF", "#FFF1F2", "#881337", "#F43F5E"),
        Palette("#881337", "#FFFFFF", "#FFFFFF", "#FFE4E6", "#4C0519", "#FB7185"),
        Palette("#9F1239", "#FFFFFF", "#FFF5F5", "#FED7D7", "#742A2A", "#E53E3E"),
        Palette("#7F1D1D", "#FFFFFF", "#FFFFFF", "#FEE2E2", "#1F2937", "#F87171"),

        // Warm Amber, Ochre & Orange
        Palette("#9A3412", "#FFFFFF", "#FFFFFF", "#FFF7ED", "#431407", "#FB923C"),
        Palette("#C2410C", "#FFFFFF", "#FFFFFF", "#FFEDD5", "#7C2D12", "#F97316"),
        Palette("#B45309", "#FFFFFF", "#FFFFFF", "#FFFBEB", "#451A03", "#FBBF24"),
        Palette("#D97706", "#FFFFFF", "#FFFFFF", "#FEF3C7", "#78350F", "#F59E0B"),
        Palette("#78350F", "#FFFFFF", "#FFFBEB", "#FEF08A", "#713F12", "#EAB308"),

        // Teal & Cyan
        Palette("#0F766E", "#FFFFFF", "#FFFFFF", "#F0FDFA", "#134E4A", "#2DD4BF"),
        Palette("#0D9488", "#FFFFFF", "#FFFFFF", "#CCFBF1", "#042F2E", "#14B8A6"),
        Palette("#115E59", "#FFFFFF", "#F0FDFA", "#E6FFFA", "#234E52", "#319795"),
        Palette("#0891B2", "#FFFFFF", "#FFFFFF", "#ECFEFF", "#155E75", "#22D3EE"),
        Palette("#0E7490", "#FFFFFF", "#FFFFFF", "#CFFAFE", "#083344", "#67E8F9"),

        // Magenta & Rose
        Palette("#831843", "#FFFFFF", "#FFFFFF", "#FDF2F8", "#500724", "#F472B6"),
        Palette("#BE185D", "#FFFFFF", "#FFFFFF", "#FCE7F3", "#701A75", "#EC4899"),
        Palette("#9D174D", "#FFFFFF", "#FFF0F5", "#FCE4EC", "#4A148C", "#D81B60"),
        Palette("#A21CAF", "#FFFFFF", "#FFFFFF", "#FAE8FF", "#4A044E", "#D946EF"),
        Palette("#701A75", "#FFFFFF", "#FFFFFF", "#F5D0FE", "#3B0764", "#C026D3"),

        // Slate & Cool Grays
        Palette("#1E293B", "#FFFFFF", "#FFFFFF", "#F8FAFC", "#0F172A", "#64748B"),
        Palette("#334155", "#FFFFFF", "#FFFFFF", "#F1F5F9", "#1E293B", "#94A3B8"),
        Palette("#0F172A", "#FFFFFF", "#FFFFFF", "#E2E8F0", "#020617", "#475569"),
        Palette("#374151", "#FFFFFF", "#FFFFFF", "#F9FAFB", "#111827", "#D1D5DB"),
        Palette("#111827", "#FFFFFF", "#FFFFFF", "#E5E7EB", "#030712", "#6B7280"),

        // Modern Pastels (Light Tint Headers)
        Palette("#E0F2FE", "#0369A1", "#FFFFFF", "#F0F9FF", "#0C4A6E", "#38BDF8"),
        Palette("#D1FAE5", "#065F46", "#FFFFFF", "#ECFDF5", "#064E3B", "#34D399"),
        Palette("#FEF3C7", "#92400E", "#FFFFFF", "#FFFBEB", "#78350F", "#FBBF24"),
        Palette("#FCE7F3", "#9D174D", "#FFFFFF", "#FDF2F8", "#831843", "#F472B6"),
        Palette("#EDE9FE", "#5B21B6", "#FFFFFF", "#F5F3FF", "#4C1D95", "#A78BFA"),
        Palette("#FFEDD5", "#9A3412", "#FFFFFF", "#FFF7ED", "#7C2D12", "#FB923C"),
        Palette("#CCFBF1", "#0F766E", "#FFFFFF", "#F0FDFA", "#134E4A", "#2DD4BF"),
        Palette("#E2E8F0", "#334155", "#FFFFFF", "#F8FAFC", "#1E293B", "#94A3B8"),

        // Dark Theme Cards
        Palette("#18181B", "#FAFAFA", "#27272A", "#3F3F46", "#FFFFFF", "#52525B"),
        Palette("#0F172A", "#F8FAFC", "#1E293B", "#334155", "#FFFFFF", "#475569")
    )

    private val presetList: List<TablePresetStyle> by lazy {
        buildAllPresets()
    }

    private fun buildAllPresets(): List<TablePresetStyle> {
        val list = mutableListOf<TablePresetStyle>()

        // 1. Price List (50 Presets)
        val priceHeaders = listOf("نمبر", "آئٹم کا نام", "قیمت (روپے)")
        val priceRowsVariants = listOf(
            listOf(
                listOf("۱", "چاول سپر کرنل (۱ کلو)", "۳۲۰"),
                listOf("۲", "دال چنا اسپیشل (۱ کلو)", "۲۸۰"),
                listOf("۳", "چکی کا آٹا (۱۰ کلو)", "۱,۴۵۰"),
                listOf("۴", "کوکنگ آئل (۱ لیٹر)", "۵۲۰"),
                listOf("۵", "چینی دانے دار (۱ کلو)", "۱۶۰")
            ),
            listOf(
                listOf("۱", "چکن کڑاہی فل", "۱,۸۵۰"),
                listOf("۲", "مٹن شنواری ہاف", "۲,۲۰۰"),
                listOf("۳", "چکن بریانی پلیٹ", "۴۵۰"),
                listOf("۴", "روغنی نان", "۶۰"),
                listOf("۵", "سلاد و رائتہ", "۱۵۰")
            ),
            listOf(
                listOf("۱", "لوگو ڈیزائننگ", "۵,۰۰۰"),
                listOf("۲", "سوشل میڈیا پوسٹ", "۱,۵۰۰"),
                listOf("۳", "کمپلیٹ ویب سائٹ", "۳۵,۰۰۰"),
                listOf("۴", "بروشر / فلائر", "۳,۰۰۰"),
                listOf("۵", "ویڈیو ایڈیٹنگ", "۸,۰۰۰")
            ),
            listOf(
                listOf("۱", "داخلہ فیس", "۵,۰۰۰"),
                listOf("۲", "ماہانہ فیس", "۴,۵۰۰"),
                listOf("۳", "سالانہ سپورٹس فنڈ", "۲,۰۰۰"),
                listOf("۴", "کمپیوٹر لیب چارجز", "۱,۲۰۰"),
                listOf("۵", "امتحانی فیس", "۱,۵۰۰")
            ),
            listOf(
                listOf("۱", "پٹرول سپر (لیٹر)", "۲۸۵"),
                listOf("۲", "ہائی سپیڈ ڈیزل (لیٹر)", "۲۹۲"),
                listOf("۳", "لائٹ ڈیزل آئل", "۱۷۵"),
                listOf("۴", "مٹی کا تیل", "۱۸۰"),
                listOf("۵", "سی این جی (کلو)", "۲۳۰")
            )
        )
        val priceFooter = listOf("", "کل رقم", "۴,۱۶۰ روپے")
        populateCategory(list, "Price List", priceHeaders, priceRowsVariants, priceFooter, hasFooter = true)

        // 2. Schedule (50 Presets)
        val scheduleHeaders = listOf("وقت", "پروگرام / سرگرمی", "مقرر / ذمہ دار")
        val scheduleRowsVariants = listOf(
            listOf(
                listOf("09:00 صبح", "تلاوت کلام پاک", "قاری محمد علی"),
                listOf("09:30 صبح", "نعت رسول مقبولﷺ", "احمد رضا قادری"),
                listOf("10:00 صبح", "خصوصی خطاب", "ڈاکٹر زاہد محمود"),
                listOf("11:30 صبح", "اجتماعی دعا", "مولانا طارق صاحب"),
                listOf("12:00 دوپہر", "کھانا و ضیافت", "انتظامیہ")
            ),
            listOf(
                listOf("08:00 صبح", "اردو لازمی", "پروفیسر حامد"),
                listOf("09:00 صبح", "انگریزی گرامر", "سر فرحان"),
                listOf("10:00 صبح", "ریاضی / الجبرا", "میم سعدیہ"),
                listOf("11:00 صبح", "جنرل سائنس", "سر عثمان"),
                listOf("12:00 دوپہر", "اسلامیات", "قاری صاحب")
            ),
            listOf(
                listOf("پیر", "ورزش و کارڈیو", "45 منٹ"),
                listOf("منگل", "بائسپس و ٹرائسپس", "40 منٹ"),
                listOf("بدھ", "چیسٹ ورک آؤٹ", "50 منٹ"),
                listOf("جمعرات", "لیگز و ایبس", "45 منٹ"),
                listOf("جمعہ", "ریسٹ و بحالی", "مکمل آرام")
            ),
            listOf(
                listOf("01 مئی", "پہلا پرچہ (اردو)", "09:00 تا 12:00"),
                listOf("03 مئی", "دوسرا پرچہ (انگریزی)", "09:00 تا 12:00"),
                listOf("05 مئی", "تیسرا پرچہ (ریاضی)", "09:00 تا 12:00"),
                listOf("07 مئی", "چوتھا پرچہ (سائنس)", "09:00 تا 12:00"),
                listOf("09 مئی", "پانچواں پرچہ (کمپیوٹر)", "09:00 تا 12:00")
            ),
            listOf(
                listOf("05:00 صبح", "فجر، ورزش و واک", "گھر / پارک"),
                listOf("07:00 صبح", "ناشتہ و تیاری", "گھر"),
                listOf("09:00 صبح", "دفتری امور و کام", "آفس"),
                listOf("05:00 شام", "مطالعہ و خاندان", "گھر"),
                listOf("10:00 رات", "سونے کی تیاری", "بیڈ روم")
            )
        )
        populateCategory(list, "Schedule", scheduleHeaders, scheduleRowsVariants, null, hasFooter = false)

        // 3. Data Table (50 Presets)
        val dataHeaders = listOf("مضمون", "کل نمبر", "حاصل کردہ", "گریڈ")
        val dataRowsVariants = listOf(
            listOf(
                listOf("اردو", "100", "88", "A+"),
                listOf("انگریزی", "100", "79", "A"),
                listOf("ریاضی", "100", "94", "A+"),
                listOf("سائنس", "100", "85", "A"),
                listOf("اسلامیات", "50", "46", "A+")
            ),
            listOf(
                listOf("جنوری", "150,000", "120,000", "+25%"),
                listOf("فروری", "180,000", "135,000", "+33%"),
                listOf("مارچ", "210,000", "150,000", "+40%"),
                listOf("اپریل", "190,000", "140,000", "+35%"),
                listOf("مئی", "240,000", "160,000", "+50%")
            ),
            listOf(
                listOf("لاہور", "25,000", "18,500", "کامیاب"),
                listOf("کراچی", "42,000", "35,000", "کامیاب"),
                listOf("اسلام آباد", "15,000", "14,200", "بہترین"),
                listOf("فیصل آباد", "18,000", "12,000", "جاری"),
                listOf("ملتان", "12,000", "9,500", "جاری")
            ),
            listOf(
                listOf("علی حسن", "92%", "پوزیشن 1", "پاس"),
                listOf("فاطمہ احمد", "89%", "پوزیشن 2", "پاس"),
                listOf("عثمان خان", "86%", "پوزیشن 3", "پاس"),
                listOf("زینب بی بی", "83%", "پوزیشن 4", "پاس"),
                listOf("بلال طارق", "78%", "پوزیشن 5", "پاس")
            ),
            listOf(
                listOf("پروجیکٹ الف", "12 مئی", "100%", "مکمل"),
                listOf("پروجیکٹ ب", "20 مئی", "75%", "جاری"),
                listOf("پروجیکٹ ج", "28 مئی", "40%", "جاری"),
                listOf("پروجیکٹ د", "05 جون", "15%", "ابتدائی"),
                listOf("پروجیکٹ ر", "15 جون", "0%", "زیر التواء")
            )
        )
        val dataFooter = listOf("ٹوٹل", "450", "392", "87.1%")
        populateCategory(list, "Data Table", dataHeaders, dataRowsVariants, dataFooter, hasFooter = true)

        // 4. Comparison (50 Presets)
        val compHeaders = listOf("خصوصیات", "بیسک (Basic)", "پرو (Pro)", "پریمیم (Max)")
        val compRowsVariants = listOf(
            listOf(
                listOf("کلاؤڈ اسٹوریج", "5 GB", "50 GB", "500 GB"),
                listOf("یوزرز کی تعداد", "1 یوزر", "5 یوزرز", "لامحدود"),
                listOf("کسٹمر سپورٹ", "ای میل", "24/7 فون", "ذاتی مینیجر"),
                listOf("ایکسپورٹ کوالٹی", "HD 720p", "Full HD 1080p", "4K Ultra HD"),
                listOf("اے آئی ٹولز", "محدود", "مکمل رسائی", "ایڈوانسڈ")
            ),
            listOf(
                listOf("قیمت", "مفت", "999 روپے", "2,499 روپے"),
                listOf("اشتہارات", "موجود", "کوئی نہیں", "کوئی نہیں"),
                listOf("اردو فونٹس", "50 فونٹس", "500+ فونٹس", "تمام فونٹس"),
                listOf("واٹر مارک", "ہاں", "نہیں", "نہیں"),
                listOf("بیک گراؤنڈ ریموور", "نہیں", "روزانہ 20", "لامحدود")
            ),
            listOf(
                listOf("وزن", "85 KG", "72 KG", "-13 KG"),
                listOf("بلڈ شوگر", "180", "110", "نارمل"),
                listOf("توانائی لیول", "کم", "زیادہ", "بہترین"),
                listOf("نیند کا دورانیہ", "5 گھنٹے", "8 گھنٹے", "پر سکون"),
                listOf("موڈ", "چڑچڑا", "خوشگوار", "مثبت")
            ),
            listOf(
                listOf("سپیڈ", "10 Mbps", "50 Mbps", "100 Mbps"),
                listOf("ڈیٹا لمٹ", "100 GB", "500 GB", "ان لمیٹڈ"),
                listOf("راؤٹر", "سنگل بینڈ", "ڈوئل بینڈ", "وائی فائی 6"),
                listOf("آئی پی ٹی وی", "نہیں", "ہاں", "ہاں + 4K"),
                listOf("تنصیب فیس", "1,500", "مفت", "مفت")
            )
        )
        populateCategory(list, "Comparison", compHeaders, compRowsVariants, null, hasFooter = false)

        // 5. Contacts (50 Presets)
        val contactHeaders = listOf("نام", "عہدہ / شعبہ", "فون نمبر", "ای میل")
        val contactRowsVariants = listOf(
            listOf(
                listOf("علی رضا", "جنرل مینیجر", "0300-1234567", "ali@example.com"),
                listOf("فاطمہ بانو", "ایڈمن آفیسر", "0311-9876543", "fatima@example.com"),
                listOf("محمد یوسف", "ہیڈ اکاؤنٹنٹ", "0322-4567890", "yousuf@example.com"),
                listOf("زینب علی", "کسٹمر سپورٹ", "0333-1122334", "zainab@example.com"),
                listOf("بلال احمد", "سافٹ ویئر انجینئر", "0344-5566778", "bilal@example.com")
            ),
            listOf(
                listOf("ڈاکٹر طاہر", "ماہر امراض قلب", "0300-5551234", "tahir@clinic.com"),
                listOf("ڈاکٹر سارہ", "ماہر امراض اطفال", "0321-4445678", "sara@clinic.com"),
                listOf("ڈاکٹر کامران", "ڈینٹل سرجن", "0333-7778899", "kamran@clinic.com"),
                listOf("ڈاکٹر عاصمہ", "ماہر امراض جلد", "0345-1112233", "asma@clinic.com"),
                listOf("ڈاکٹر عدنان", "فزیوتھراپسٹ", "0312-9990011", "adnan@clinic.com")
            ),
            listOf(
                listOf("احمد حسن", "کلاس انچارج", "0301-2233445", "سائنس ڈیپارٹمنٹ"),
                listOf("میم عائشہ", "وائس پرنسپل", "0313-6677889", "ایڈمنسٹریشن"),
                listOf("قاری طارق", "اسلامیات استاد", "0331-8899001", "اسلامک اسٹڈیز"),
                listOf("سر وقاص", "سپورٹس انچارج", "0342-3344556", "فزیکل ایجوکیشن"),
                listOf("میم صبا", "لائبریرین", "0324-5566778", "لائبریری")
            )
        )
        populateCategory(list, "Contacts", contactHeaders, contactRowsVariants, null, hasFooter = false)

        // 6. Tracking (50 Presets)
        val trackHeaders = listOf("کام / ہدف", "ذمہ دار", "ڈیڈ لائن", "حیثیت")
        val trackRowsVariants = listOf(
            listOf(
                listOf("ویب سائٹ ڈیزائن", "علی حسن", "10 مئی", "مکمل ✓"),
                listOf("موبائل ایپ ڈیویلپمنٹ", "احمد رضا", "20 مئی", "جاری ⏳"),
                listOf("ڈیجیٹل مارکیٹنگ پلان", "سارہ خان", "25 مئی", "جاری ⏳"),
                listOf("کلائنٹ فیڈ بیک", "زینب", "28 مئی", "باقی ⭕"),
                listOf("فائنل ٹیسٹنگ", "ٹیم", "05 جون", "باقی ⭕")
            ),
            listOf(
                listOf("روزانہ 30 منٹ واک", "صحت", "روزانہ", "مکمل ✓"),
                listOf("کتاب کا مطالعہ (10 صفحات)", "تعلیم", "روزانہ", "مکمل ✓"),
                listOf("8 گلاس پانی پینا", "صحت", "روزانہ", "جاری ⏳"),
                listOf("نئے الفاظ یاد کرنا", "زبان", "روزانہ", "مکمل ✓"),
                listOf("جلدی سونا (10 بجے)", "معمول", "روزانہ", "جاری ⏳")
            ),
            listOf(
                listOf("آفس کرایہ کی ادائیگی", "اکاؤنٹس", "01 مئی", "ادا شدہ ✓"),
                listOf("بجلی کا بل", "ایڈمن", "05 مئی", "ادا شدہ ✓"),
                listOf("انٹرنیٹ بل", "آئی ٹی", "10 مئی", "ادا شدہ ✓"),
                listOf("ملازمین کی تنخواہیں", "اکاؤنٹس", "10 مئی", "پینڈنگ ⏳"),
                listOf("سپلائرز کی ادائیگی", "خریداری", "15 مئی", "پینڈنگ ⏳")
            )
        )
        val trackFooter = listOf("کل پیش رفت", "4/5 مکمل", "80% ٹارگٹ", "کامیاب")
        populateCategory(list, "Tracking", trackHeaders, trackRowsVariants, trackFooter, hasFooter = true)

        // 7. Islamic (50 Presets)
        val islamicHeaders = listOf("نماز", "اذان کا وقت", "جماعت کا وقت", "قاری / امام")
        val islamicRowsVariants = listOf(
            listOf(
                listOf("فجر", "04:35 صبح", "05:00 صبح", "قاری محمد علی"),
                listOf("ظہر", "12:30 دوپہر", "01:15 دوپہر", "مولانا طارق"),
                listOf("عصر", "04:45 شام", "05:15 شام", "قاری احمد رضا"),
                listOf("مغرب", "06:58 شام", "07:05 شام", "مولانا طارق"),
                listOf("عشاء", "08:15 رات", "08:45 رات", "حافظ عثمان")
            ),
            listOf(
                listOf("01 رمضان", "04:28 صبح", "06:45 شام", "08:30 رات"),
                listOf("02 رمضان", "04:27 صبح", "06:46 شام", "08:30 رات"),
                listOf("03 رمضان", "04:26 صبح", "06:47 شام", "08:30 رات"),
                listOf("04 رمضان", "04:25 صبح", "06:48 شام", "08:30 رات"),
                listOf("05 رمضان", "04:24 صبح", "06:49 شام", "08:30 رات")
            ),
            listOf(
                listOf("سبحان اللہ", "روزانہ 100 مرتبہ", "صبح و شام", "مکمل ✓"),
                listOf("الحمد للہ", "روزانہ 100 مرتبہ", "صبح و شام", "مکمل ✓"),
                listOf("اللہ اکبر", "روزانہ 100 مرتبہ", "صبح و شام", "مکمل ✓"),
                listOf("استغفر اللہ", "روزانہ 100 مرتبہ", "ہر نماز بعد", "مکمل ✓"),
                listOf("درود شریف", "روزانہ 100 مرتبہ", "خصوصاً جمعہ", "جاری ⏳")
            )
        )
        populateCategory(list, "Islamic", islamicHeaders, islamicRowsVariants, null, hasFooter = false)

        // 8. Card Style (50 Presets - Rounded corners)
        val cardHeaders = listOf("پراپرٹی", "تفصیلات", "حیثیت")
        val cardRowsVariants = listOf(
            listOf(
                listOf("ڈسپلے", "6.8 انچ Dynamic AMOLED 2X", "120Hz"),
                listOf("پروسیسر", "Snapdragon 8 Gen 3", "Octa-Core"),
                listOf("ریم / میموری", "12 GB RAM / 512 GB", "UFS 4.0"),
                listOf("کیمرہ", "200 MP + 50 MP + 12 MP", "8K ویڈیو"),
                listOf("بیٹری", "5000 mAh فاسٹ چارجنگ", "45W فاسٹ")
            ),
            listOf(
                listOf("گاڑی کا ماڈل", "ٹویوٹا کرولا آلٹس گرینڈے", "2024"),
                listOf("انجن کیپسٹی", "1800cc VVT-i", "پٹرول"),
                listOf("ٹرانسمیشن", "سی وی ٹی آٹو میٹک", "7 سپیڈ"),
                listOf("مائلیج", "14 تا 16 کلومیٹر فی لیٹر", "بہترین"),
                listOf("رنگ", "سپر وائٹ", "برانڈ نیو")
            ),
            listOf(
                listOf("گھر کی لوکیشن", "ڈی ایچ اے فیز 5، لاہور", "پرائم لوکیشن"),
                listOf("رقبہ", "1 کنال (500 گز)", "ڈبل سٹوری"),
                listOf("بیڈ رومز", "5 ماسٹر بیڈ اٹیچ باتھ", "لگژری ٹائلز"),
                listOf("کچن", "2 ماڈرن کچن امپورٹڈ", "اوون فٹڈ"),
                listOf("ڈیمانڈ", "6 کروڑ 50 لاکھ", "فائنل ریٹ")
            )
        )
        populateCategory(list, "Card Style", cardHeaders, cardRowsVariants, null, hasFooter = false, defaultCornerRadius = 12f)

        // 9. Business (50 Presets)
        val bizHeaders = listOf("شعبہ / مد", "بجٹ (روپے)", "اصل خرچ", "بچت / فرق")
        val bizRowsVariants = listOf(
            listOf(
                listOf("مارکیٹنگ و اشتہارات", "150,000", "125,000", "+25,000 بچت"),
                listOf("آفس کرایہ و یوٹیلٹیز", "95,000", "92,500", "+2,500 بچت"),
                listOf("سافٹ ویئر و ٹولز", "45,000", "48,000", "-3,000 اضافی"),
                listOf("ملازمین کی ویلفیئر", "30,000", "28,000", "+2,000 بچت"),
                listOf("سفر و ٹرانسپورٹ", "25,000", "20,000", "+5,000 بچت")
            ),
            listOf(
                listOf("پروڈکٹ A", "1,200 یونٹس", "480,000", "+35% گروتھ"),
                listOf("پروڈکٹ B", "850 یونٹس", "340,000", "+20% گروتھ"),
                listOf("پروڈکٹ C", "600 یونٹس", "180,000", "+12% گروتھ"),
                listOf("پروڈکٹ D", "450 یونٹس", "225,000", "+18% گروتھ"),
                listOf("پروڈکٹ E", "300 یونٹس", "150,000", "+8% گروتھ")
            ),
            listOf(
                listOf("سہ ماہی Q1", "1,250,000", "850,000", "400,000 منافع"),
                listOf("سہ ماہی Q2", "1,450,000", "920,000", "530,000 منافع"),
                listOf("سہ ماہی Q3", "1,600,000", "1,050,000", "550,000 منافع"),
                listOf("سہ ماہی Q4", "1,900,000", "1,200,000", "700,000 منافع")
            )
        )
        val bizFooter = listOf("کل میزان", "345,000", "313,500", "+31,500 بچت")
        populateCategory(list, "Business", bizHeaders, bizRowsVariants, bizFooter, hasFooter = true)

        // 10. Classic (50 Presets)
        val classicHeaders = listOf("نمبر شمار", "تفصیل / نام", "شعبہ", "کیفیت")
        val classicRowsVariants = listOf(
            listOf(
                listOf("۱", "محمد ارشد", "اکاؤنٹس", "مستقل"),
                listOf("۲", "سید وقار علی", "ایڈمنسٹریشن", "مستقل"),
                listOf("۳", "طارق عزیز", "سیلز و مارکیٹنگ", "پروبیشن"),
                listOf("۴", "عمران حیدر", "سافٹ ویئر ڈویلپمنٹ", "مستقل"),
                listOf("۵", "بلال حسین", "کسٹمر سپورٹ", "مستقل")
            ),
            listOf(
                listOf("۱", "پہلا سبق", "صفحہ ۱ تا ۱۰", "مکمل"),
                listOf("۲", "دوسرا سبق", "صفحہ ۱۱ تا ۲۵", "مکمل"),
                listOf("۳", "تیسرا سبق", "صفحہ ۲۶ تا ۴۰", "جاری"),
                listOf("۴", "چوتھا سبق", "صفحہ ۴۱ تا ۶۰", "باقی"),
                listOf("۵", "پانچواں سبق", "صفحہ ۶۱ تا ۸۰", "باقی")
            ),
            listOf(
                listOf("۱", "کمپیوٹر کورس", "۳ ماہ", "سرٹیفکیٹ"),
                listOf("۲", "گرافک ڈیزائننگ", "۲ ماہ", "ڈپلومہ"),
                listOf("۳", "ویب ڈویلپمنٹ", "۶ ماہ", "پروفیشنل"),
                listOf("۴", "ڈیجیٹل مارکیٹنگ", "۱ ماہ", "شارٹ کورس"),
                listOf("۵", "انگلش لینگویج", "۲ ماہ", "بیسک")
            )
        )
        populateCategory(list, "Classic", classicHeaders, classicRowsVariants, null, hasFooter = false)

        return list
    }

    private fun populateCategory(
        outList: MutableList<TablePresetStyle>,
        categoryName: String,
        headers: List<String>,
        rowsVariants: List<List<List<String>>>,
        footer: List<String>?,
        hasFooter: Boolean,
        defaultCornerRadius: Float = 0f
    ) {
        val borderModes = listOf(
            TableBorderMode.ALL,
            TableBorderMode.HORIZONTAL,
            TableBorderMode.OUTER,
            TableBorderMode.ALL,
            TableBorderMode.HORIZONTAL
        )

        for (i in 0 until 50) {
            val palette = palettes[(i + categories.indexOf(categoryName) * 5) % palettes.size]
            val rowsVariant = rowsVariants[i % rowsVariants.size]
            val bMode = if (categoryName == "Classic") {
                TableBorderMode.ALL
            } else if (categoryName == "Card Style") {
                if (i % 2 == 0) TableBorderMode.OUTER else TableBorderMode.NONE
            } else {
                borderModes[i % borderModes.size]
            }

            val bWidth = when (i % 3) {
                0 -> 1.0f
                1 -> 1.5f
                else -> 2.0f
            }

            val cornerR = if (categoryName == "Card Style" || i % 5 == 0) {
                if (defaultCornerRadius > 0f) defaultCornerRadius else 10f
            } else {
                0f
            }

            val isFooterEnabled = hasFooter && (i % 2 == 0 || categoryName in listOf("Price List", "Data Table", "Business"))
            val totalRowCount = 1 + rowsVariant.size + (if (isFooterEnabled) 1 else 0)

            outList.add(
                TablePresetStyle(
                    id = "tbl_${categoryName.lowercase().replace(' ', '_')}_${i + 1}",
                    name = "$categoryName ${i + 1}",
                    category = categoryName,
                    headerBgColor = Color.parseColor(palette.h),
                    headerTextColor = Color.parseColor(palette.ht),
                    row1BgColor = Color.parseColor(palette.r1),
                    row2BgColor = Color.parseColor(palette.r2),
                    bodyTextColor = Color.parseColor(palette.bt),
                    borderColor = Color.parseColor(palette.b),
                    borderWidth = bWidth,
                    borderMode = bMode,
                    rows = totalRowCount,
                    cols = headers.size,
                    hasHeader = true,
                    hasFooter = isFooterEnabled,
                    cornerRadius = cornerR,
                    prefillHeaders = headers,
                    prefillRows = rowsVariant,
                    prefillFooter = if (isFooterEnabled) footer else null,
                    isRTL = true
                )
            )
        }
    }

    fun getPresetsByCategory(cat: String): List<TablePresetStyle> {
        return presetList.filter { it.category.equals(cat, ignoreCase = true) }
    }

    fun applyPresetToTable(preset: TablePresetStyle, tableData: TableData) {
        tableData.rows = preset.rows
        tableData.cols = preset.cols
        tableData.hasHeader = preset.hasHeader
        tableData.hasFooter = preset.hasFooter
        tableData.hasHeaderCol = preset.hasHeaderCol
        tableData.cornerRadius = preset.cornerRadius
        tableData.borderWidth = preset.borderWidth
        tableData.borderColor = preset.borderColor
        tableData.borderMode = preset.borderMode
        tableData.paddingH = preset.paddingH
        tableData.paddingV = preset.paddingV
        tableData.isRTL = preset.isRTL

        // Rebuild cells 2D array
        tableData.cells = MutableList(preset.rows) {
            MutableList(preset.cols) { TableCell() }
        }

        // Base & Header styles
        tableData.base.bgColor = preset.row1BgColor
        tableData.base.textColor = preset.bodyTextColor
        tableData.base.isBold = preset.bodyBold
        tableData.base.hAlign = TextAlignment.RIGHT
        tableData.base.vAlign = VAlign.MIDDLE

        tableData.headerStyle.bgColor = preset.headerBgColor
        tableData.headerStyle.textColor = preset.headerTextColor
        tableData.headerStyle.isBold = preset.headerBold
        tableData.headerStyle.hAlign = TextAlignment.RIGHT
        tableData.headerStyle.vAlign = VAlign.MIDDLE

        if (preset.hasFooter) {
            tableData.footerStyle.bgColor = preset.footerBgColor ?: preset.headerBgColor
            tableData.footerStyle.textColor = preset.footerTextColor ?: preset.headerTextColor
            tableData.footerStyle.isBold = preset.footerBold
            tableData.footerStyle.hAlign = TextAlignment.RIGHT
            tableData.footerStyle.vAlign = VAlign.MIDDLE
        }

        // Alternating row colors
        tableData.rowStyles.clear()
        val startRow = if (preset.hasHeader) 1 else 0
        val endRow = if (preset.hasFooter) preset.rows - 1 else preset.rows
        for (r in startRow until endRow) {
            val bodyRowIndex = r - startRow
            val rowStyle = tableData.rowStyles.getOrPut(r) { TableTextStyle() }
            rowStyle.bgColor = if (bodyRowIndex % 2 == 1) preset.row2BgColor else preset.row1BgColor
            rowStyle.textColor = preset.bodyTextColor
            rowStyle.hAlign = TextAlignment.RIGHT
            rowStyle.vAlign = VAlign.MIDDLE
        }

        // Pre-fill header text
        preset.prefillHeaders?.let { headers ->
            if (preset.hasHeader && preset.rows > 0) {
                for (c in 0 until minOf(headers.size, preset.cols)) {
                    tableData.cells[0][c].text = headers[c]
                }
            }
        }

        // Pre-fill body rows text
        preset.prefillRows?.let { rows ->
            val dataStart = if (preset.hasHeader) 1 else 0
            for ((idx, rowData) in rows.withIndex()) {
                val r = dataStart + idx
                if (r >= preset.rows) break
                for (c in 0 until minOf(rowData.size, preset.cols)) {
                    tableData.cells[r][c].text = rowData[c]
                }
            }
        }

        // Pre-fill footer text
        preset.prefillFooter?.let { footerData ->
            if (preset.hasFooter && preset.rows > 0) {
                val fRow = preset.rows - 1
                for (c in 0 until minOf(footerData.size, preset.cols)) {
                    tableData.cells[fRow][c].text = footerData[c]
                }
            }
        }

        tableData.selectedCells.clear()
    }
}
