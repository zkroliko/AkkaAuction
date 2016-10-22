package tools

import com.github.nscala_time.time.Imports._

object TimeTools {
  def timeNow: String = {
    timeFormatted(DateTime.now())
  }

  def timeFormatted(dateTime: DateTime): String = {
    dateTime.toString(DateTimeFormat.fullTime())
  }
}
