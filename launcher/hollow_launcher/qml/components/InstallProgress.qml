import QtQuick

// Install / update progress: the five steps as glowing nodes on a line, and a bar with a moving shimmer.
Item {
    id: ip
    property int step: backend.installStep
    property real value: backend.progress   // -1 = working, size unknown
    property var steps: backend.installSteps
    implicitHeight: 92

    property real shown: value < 0 ? 0 : value
    Behavior on shown { SpringAnimation { spring: 2.5; damping: 0.5; epsilon: 0.001 } }

    Row {
        id: nodes
        anchors.left: parent.left
        anchors.right: parent.right
        height: 44
        Repeater {
            model: ip.steps
            Item {
                width: nodes.width / ip.steps.length
                height: 44
                readonly property bool done: index < ip.step
                readonly property bool current: index === ip.step
                // line to the next node
                Rectangle {
                    visible: index < ip.steps.length - 1
                    x: parent.width / 2 + 9
                    y: 10
                    width: parent.width - 18
                    height: 2
                    radius: 1
                    color: Qt.rgba(1, 1, 1, 0.08)
                    Rectangle {
                        height: parent.height
                        radius: 1
                        width: done ? parent.width : (current ? parent.width * ip.shown : 0)
                        Behavior on width { NumberAnimation { duration: 300 } }
                        gradient: Gradient {
                            orientation: Gradient.Horizontal
                            GradientStop { position: 0; color: app.start }
                            GradientStop { position: 1; color: app.end }
                        }
                    }
                }
                Rectangle {
                    id: dot
                    x: parent.width / 2 - 9
                    width: 18
                    height: 18
                    radius: 9
                    color: done ? app.accent : (current ? Qt.rgba(app.accent.r, app.accent.g, app.accent.b, 0.25) : Qt.rgba(1, 1, 1, 0.06))
                    border.width: 2
                    border.color: done || current ? app.accent : Qt.rgba(1, 1, 1, 0.14)
                    Behavior on color { ColorAnimation { duration: 250 } }
                    Icon {
                        anchors.centerIn: parent
                        visible: done
                        name: "check"
                        size: 12
                        stroke: 2
                        color: "#ffffff"
                    }
                    Rectangle {
                        anchors.centerIn: parent
                        visible: current
                        width: parent.width + pulse * 14
                        height: width
                        radius: width / 2
                        color: "transparent"
                        border.width: 2
                        border.color: Qt.rgba(app.accent.r, app.accent.g, app.accent.b, 0.6 * (1 - pulse))
                        property real pulse: 0
                        NumberAnimation on pulse { from: 0; to: 1; duration: 1100; loops: Animation.Infinite; running: current }
                    }
                }
                Text {
                    anchors.horizontalCenter: dot.horizontalCenter
                    y: 24
                    text: modelData
                    color: current ? app.text : (done ? app.dim : app.faint)
                    font.family: app.fontFamily
                    font.pixelSize: 12
                    font.weight: current ? Font.DemiBold : Font.Normal
                }
            }
        }
    }
    Rectangle {
        id: bar
        anchors.left: parent.left
        anchors.right: pct.left
        anchors.rightMargin: 12
        y: 58
        height: 8
        radius: 4
        color: Qt.rgba(1, 1, 1, 0.07)
        clip: true
        Rectangle {
            height: parent.height
            radius: 4
            width: ip.value < 0 ? parent.width * 0.3 : Math.max(8, parent.width * ip.shown)
            x: ip.value < 0 ? travel : 0
            property real travel: 0
            SequentialAnimation on travel {
                running: ip.value < 0 && ip.visible
                loops: Animation.Infinite
                NumberAnimation { from: -bar.width * 0.3; to: bar.width; duration: 1300; easing.type: Easing.InOutQuad }
            }
            gradient: Gradient {
                orientation: Gradient.Horizontal
                GradientStop { position: 0; color: app.start }
                GradientStop { position: 1; color: app.end }
            }
            Rectangle {
                id: shimmer
                width: 60
                height: parent.height
                opacity: 0.5
                gradient: Gradient {
                    orientation: Gradient.Horizontal
                    GradientStop { position: 0; color: Qt.rgba(1, 1, 1, 0) }
                    GradientStop { position: 0.5; color: Qt.rgba(1, 1, 1, 0.8) }
                    GradientStop { position: 1; color: Qt.rgba(1, 1, 1, 0) }
                }
                NumberAnimation on x { from: -60; to: bar.width; duration: 1200; loops: Animation.Infinite; running: ip.visible }
            }
        }
    }
    Text {
        id: pct
        anchors.right: parent.right
        y: 52
        width: 46
        horizontalAlignment: Text.AlignRight
        text: ip.value < 0 ? "..." : Math.round(ip.shown * 100) + "%"
        color: app.text
        font.family: app.fontFamily
        font.pixelSize: 14
        font.weight: Font.DemiBold
    }
    Text {
        anchors.left: parent.left
        anchors.right: parent.right
        y: 72
        text: backend.status
        color: app.dim
        font.family: app.fontFamily
        font.pixelSize: 12
        elide: Text.ElideRight
    }
}
