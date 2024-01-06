package com.ibaevzz.bdev

object Constants {

    val RESULT_TYPE = arrayOf("INVALID SERIAL PORT",
        "CRC PASSED",
        "CRC FAILED",
        "TIMEOUT",
        "INVALID INPUT",
        "POINTLESS",
        "NOT SUPPORTED")

    val TVH_DEVICE = setOf(297 to "Пульсар модуль счетчика воды v6",
        319 to "Пульсар модуль счетчика воды v1.1",
        338 to "Пульсар модуль счетчика воды v1.9",
        385 to "Пульсар модуль счетчика воды Mini v1",
        408 to "Пульсар модуль счетчика воды v1.20",
        98  to "Пульсар водосчётчик RS485")

    val CURSED_IDS = arrayOf(297, 98)

    val PULSAR_ERROR = arrayOf("Успех",
        "Отсутствует запрашиваемый код функции",
        "Ошибка в битовой маске запроса",
        "Ошибочная длинна запроса",
        "Отсутствует параметр",
        "Запись заблокирована, требуется авторизация",
        "Записываемое значение (параметр) находится вне заданного диапазона",
        "Отсутствует запрашиваемый тип архива",
        "Превышение максимального количества архивных значений за один пакет")

    val ADDRESS_REQUEST = arrayOf(
        byteArrayOf(0xf0.toByte(), 0x0f, 0x0f, 0xf0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00),
        byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x0a, 0x0c, 0x01, 0x00, 0x01, 0x00),
        byteArrayOf(0x0a, 0x0c, 0x00, 0x00, 0x01, 0x00),
        byteArrayOf(0x03, 0x02, 0x46, 0x00, 0x01)
    )
}