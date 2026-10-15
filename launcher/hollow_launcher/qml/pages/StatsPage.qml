import QtQuick
import "../components"

// Stats: play time from the launcher's own log of your sessions, and counts from your game folder.
Item {
    id: page
    property real appear: 1
    readonly property var s: backend.statsData
    readonly property var c: backend.folderCounts

    onAppearChanged: if (appear < 0.05) chart.grow = 0
    property bool shown: appear > 0.5
    onShownChanged: if (shown) { backend.refreshStats(); growAnim.restart() }
    NumberAnimation { id: growAnim; target: chart; property: "grow"; from: 0; to: 1; duration: 1200; easing.type: Easing.OutCubic }

    Row {
        id: tiles
        anchors.left: parent.left
        anchors.right: parent.right
        height: 96
        spacing: 12
        Repeater {
            model: [
                { label: "Played in total", value: (page.s.totalHours || 0) + " h" },
                { label: "This week", value: Math.round((page.s.weekMinutes || 0) / 6) / 10 + " h" },
                { label: "Sessions", value: String(page.s.sessions || 0) },
                { label: "Longest session", value: (page.s.longestMinutes || 0) + " min" },
                { label: "Day streak", value: String(page.s.streak || 0) }
            ]
            GlassPanel {
                width: (tiles.width - tiles.spacing * 4) / 5
                height: tiles.height
                opacity: Math.max(0, Math.min(1, page.appear * 1.6 - index * 0.12))
                transform: Translate { y: 16 * (1 - Math.max(0, Math.min(1, page.appear * 1.6 - index * 0.12))) }
                Column {
                    x: 16
                    anchors.verticalCenter: parent.verticalCenter
                    spacing: 4
                    Text {
                        text: modelData.value
                        color: app.text
                        font.family: app.fontFamily
                        font.pixelSize: 26
                        font.weight: Font.Bold
                    }
                    Text {
                        text: modelData.label
                        color: app.faint
                        font.family: app.fontFamily
                        font.pixelSize: 12
                    }
                }
            }
        }
    }

    GlassPanel {
        id: chartPanel
        anchors.top: tiles.bottom
        anchors.topMargin: 14
        anchors.left: parent.left
        anchors.right: parent.right
        height: Math.max(200, page.height - tiles.height - countsPanel.height - 30)
        opacity: page.appear
        SectionTitle {
            x: 18
            y: 14
            text: "Last 14 days"
        }
        Text {
            anchors.right: parent.right
            anchors.rightMargin: 18
            y: 16
            text: "average session " + (page.s.averageMinutes || 0) + " min"
            color: app.faint
            font.family: app.fontFamily
            font.pixelSize: 12
        }
        BarChart {
            id: chart
            x: 18
            y: 52
            width: parent.width - 36
            height: parent.height - 70
            days: page.s.chart || []
        }
    }

    GlassPanel {
        id: countsPanel
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: 92
        opacity: page.appear
        Row {
            anchors.fill: parent
            anchors.margins: 16
            Repeater {
                model: [
                    { label: "Screenshots", value: page.c.screenshots, icon: "image" },
                    { label: "Worlds", value: page.c.worlds, icon: "globe" },
                    { label: "Waypoints", value: page.c.waypoints, icon: "spark" },
                    { label: "Your capes", value: page.c.capes, icon: "wardrobe" },
                    { label: "Songs", value: page.c.songs, icon: "music" },
                    { label: "Hollow modules on", value: page.c.modulesOn, icon: "settings" }
                ]
                Item {
                    width: parent.width / 6
                    height: parent.height
                    Icon {
                        name: modelData.icon
                        color: app.accent
                        anchors.verticalCenter: parent.verticalCenter
                    }
                    Column {
                        x: 28
                        anchors.verticalCenter: parent.verticalCenter
                        Text {
                            text: String(modelData.value || 0)
                            color: app.text
                            font.family: app.fontFamily
                            font.pixelSize: 20
                            font.weight: Font.Bold
                        }
                        Text {
                            text: modelData.label
                            color: app.faint
                            font.family: app.fontFamily
                            font.pixelSize: 11
                        }
                    }
                }
            }
        }
    }
}
