package hu.angolmagyar.fordito.intelligence
import org.junit.Assert.*
import org.junit.Test
class DictionaryCsvTest {
 @Test fun roundTripsCommaAndQuotes() {
  val source = listOf(DictionaryEntry("hello", "szia, üdv", "He said \"hello\"."))
  assertEquals(source, DictionaryCsv.parse(DictionaryCsv.export(source)))
 }
 @Test fun acceptsUtf8BomBeforeHeader() {
  val csv = "\uFEFFenglish,hungarian,example\nhello,szia,\n"
  assertEquals(listOf(DictionaryEntry("hello", "szia", "")), DictionaryCsv.parse(csv))
 }
}
