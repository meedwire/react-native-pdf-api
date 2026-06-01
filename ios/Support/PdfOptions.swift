import UIKit

internal func optionsNumber(
  _ options: [String: Any]?,
  _ key: String,
  default defaultValue: Double
) -> Double {
  if let value = options?[key] as? Double {
    return value
  }

  if let value = options?[key] as? Int {
    return Double(value)
  }

  return defaultValue
}

internal func optionsInt(
  _ options: [String: Any]?,
  _ key: String
) -> Int? {
  if let value = options?[key] as? Int {
    return value
  }

  if let value = options?[key] as? Double {
    return Int(value)
  }

  return nil
}

internal func optionsString(
  _ options: [String: Any]?,
  _ key: String,
  default defaultValue: String
) -> String {
  options?[key] as? String ?? defaultValue
}

internal func parseColor(
  _ color: String?,
  default defaultColor: UIColor = .white
) -> UIColor {
  guard let color else {
    return defaultColor
  }

  let normalized = color
    .trimmingCharacters(in: .whitespacesAndNewlines)
    .replacingOccurrences(of: "#", with: "")

  guard let value = UInt64(normalized, radix: 16) else {
    return defaultColor
  }

  switch normalized.count {
  case 6:
    return UIColor(
      red: CGFloat((value & 0xFF0000) >> 16) / 255,
      green: CGFloat((value & 0x00FF00) >> 8) / 255,
      blue: CGFloat(value & 0x0000FF) / 255,
      alpha: 1
    )
  case 8:
    return UIColor(
      red: CGFloat((value & 0xFF000000) >> 24) / 255,
      green: CGFloat((value & 0x00FF0000) >> 16) / 255,
      blue: CGFloat((value & 0x0000FF00) >> 8) / 255,
      alpha: CGFloat(value & 0x000000FF) / 255
    )
  default:
    return defaultColor
  }
}

internal func isoDate(_ value: Any?) -> String? {
  guard let date = value as? Date else {
    return nil
  }

  return ISO8601DateFormatter().string(from: date)
}
