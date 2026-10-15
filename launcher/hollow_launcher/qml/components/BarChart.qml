import QtQuick

// Minutes played per day: bars rise one after another when shown; hover a bar for the exact time.
Item {
    id: chart
    property var days: []      // [{ label, date, minutes }]
    property real grow: 0      // 0..1, animated by the page
    readonly property real maxMinutes: {
        var m = 30
        for (var i = 0; i < days.length; i++) m = Math.max(m, days[i].minutes)
        return m
    }
    property int hovered: -1

    function fmt(min) {
        if (min < 1) return "not played"
        var h = Math.floor(min / 60)
        var m = Math.round(min % 60)
        return h > 0 ? h + " h " + m + " min" : m + " min"
    }

    // guide lines
    Repeater {
        model: 3
        Rectangle {
            x: 0
            width: chart.width
            height: 1
            y: (chart.height - 22) * (index / 3)
            color: Qt.rgba(1, 1, 1, 0.05)
        }
    }
    Row {
        id: bars
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 22
        spacing: 6
        Repeater {
            model: chart.days
            Item {
                width: (bars.width - bars.spacing * (chart.days.length - 1)) / Math.max(1, chart.days.length)
                height: bars.height
                readonly property real local: Math.max(0, Math.min(1, chart.grow * 1.6 - index * 0.04))
                Rectangle {
                    anchors.bottom: parent.bottom
                    width: parent.width
                    height: Math.max(3, parent.height * (modelData.minutes / chart.maxMinutes) * (1 - Math.pow(1 - local, 3)))
                    radius: Math.min(6, width / 2)
                    opacity: modelData.minutes > 0 ? 1 : 0.35
                    gradient: Gradient {
                        GradientStop { position: 0; color: Qt.lighter(app.end, chart.hovered === index ? 1.35 : 1) }
                        GradientStop { position: 1; color: Qt.lighter(app.start, chart.hovered === index ? 1.35 : 1) }
                    }
                }
                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.top: parent.bottom
                    anchors.topMargin: 6
                    text: modelData.label
                    color: index === chart.days.length - 1 ? app.text : app.faint
                    font.family: app.fontFamily
                    font.pixelSize: 11
                }
                HoverHandler {
                    onHoveredChanged: chart.hovered = hovered ? index : (chart.hovered === index ? -1 : chart.hovered)
                }
            }
        }
    }
    Rectangle {
        id: tip
        visible: chart.hovered >= 0 && chart.hovered < chart.days.length
        readonly property var entry: visible ? chart.days[chart.hovered] : null
        width: tipText.implicitWidth + 18
        height: 26
        radius: 6
        color: "#1b1c28"
        border.width: 1
        border.color: Qt.rgba(app.accent.r, app.accent.g, app.accent.b, 0.5)
        x: Math.max(0, Math.min(chart.width - width, (chart.hovered + 0.5) * (bars.width + bars.spacing) / Math.max(1, chart.days.length) - width / 2))
        y: -6
        Text {
            id: tipText
            anchors.centerIn: parent
            text: tip.entry ? tip.entry.date + ": " + chart.fmt(tip.entry.minutes) : ""
            color: app.text
            font.family: app.fontFamily
            font.pixelSize: 12
        }
    }
}
