import QtQuick
import Hollow
import "../components"

// Wardrobe: your skin in 3D with your Hollow cosmetics, plus the mods and the folders that hold your styles.
Item {
    id: page
    property real appear: 1

    GlassPanel {
        id: viewer
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        width: Math.min(parent.width * 0.42, 400)
        opacity: page.appear
        transform: Translate { x: -20 * (1 - page.appear) }

        SkinView {
            id: skin
            anchors.fill: parent
            anchors.topMargin: 40
            anchors.bottomMargin: 56
            skinPath: backend.skinPath
            capePath: backend.cosmetics.cape || ""
            wingsPath: backend.cosmetics.wings || ""
            haloColor: backend.cosmetics.halo === "theme" ? app.accent : (backend.cosmetics.halo ? backend.cosmetics.halo : "transparent")
            accent: app.accent
            animate: backend.animatedBackground
            property real spin: 0
            yaw: 28 + spin
        }
        // drag to turn; after a few idle seconds it keeps turning slowly
        MouseArea {
            anchors.fill: skin
            cursorShape: pressed ? Qt.ClosedHandCursor : Qt.OpenHandCursor
            property real lastX: 0
            property real lastY: 0
            onPressed: (m) => { lastX = m.x; lastY = m.y; idle.restart(); autoSpin.stop() }
            onPositionChanged: (m) => {
                skin.spin += (m.x - lastX) * 0.8
                skin.pitch = Math.max(-30, Math.min(40, skin.pitch + (m.y - lastY) * 0.35))
                lastX = m.x
                lastY = m.y
            }
            onReleased: idle.restart()
        }
        Timer {
            id: idle
            interval: 3500
            running: true
            onTriggered: autoSpin.start()
        }
        NumberAnimation {
            id: autoSpin
            target: skin
            property: "spin"
            from: skin.spin
            to: skin.spin + 360
            duration: 16000
            loops: Animation.Infinite
            running: false
        }
        Text {
            x: 18
            y: 14
            text: "Your look"
            color: app.text
            font.family: app.fontFamily
            font.pixelSize: 15
            font.weight: Font.Bold
        }
        Text {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.margins: 16
            text: "Drag to turn. Change capes, wings, halo and more in game: Right Shift → Styles."
            color: app.faint
            font.family: app.fontFamily
            font.pixelSize: 12
            wrapMode: Text.WordWrap
            horizontalAlignment: Text.AlignHCenter
        }
    }

    Column {
        anchors.left: viewer.right
        anchors.leftMargin: 16
        anchors.right: parent.right
        anchors.top: parent.top
        spacing: 16
        opacity: page.appear
        transform: Translate { x: 20 * (1 - page.appear) }

        GlassPanel {
            width: parent.width
            height: modsCol.implicitHeight + 34
            Column {
                id: modsCol
                x: 18
                y: 16
                width: parent.width - 36
                spacing: 10
                Item {
                    width: parent.width
                    height: 30
                    SectionTitle { text: "Mods"; anchors.verticalCenter: parent.verticalCenter }
                    Row {
                        anchors.right: parent.right
                        spacing: 8
                        HButton {
                            small: true
                            icon: "refresh"
                            text: "Check"
                            onClicked: backend.checkUpdates()
                        }
                        HButton {
                            small: true
                            primary: true
                            text: "Update all"
                            enabled: backend.updatesAvailable && !backend.isBusy
                            onClicked: backend.updateMods()
                        }
                    }
                }
                Repeater {
                    model: backend.modRows
                    Rectangle {
                        width: modsCol.width
                        height: 44
                        radius: app.radius + 2
                        color: Qt.rgba(1, 1, 1, 0.04)
                        Column {
                            x: 14
                            anchors.verticalCenter: parent.verticalCenter
                            spacing: 2
                            Text {
                                text: modelData.name
                                color: app.text
                                font.family: app.fontFamily
                                font.pixelSize: 14
                                font.weight: Font.DemiBold
                            }
                            Text {
                                text: modelData.installed !== "" ? "installed " + modelData.installed + (modelData.latest !== "" && modelData.latest !== modelData.installed ? "  ·  newest " + modelData.latest : "") : "not installed"
                                color: app.faint
                                font.family: app.fontFamily
                                font.pixelSize: 12
                            }
                        }
                        Rectangle {
                            anchors.right: parent.right
                            anchors.rightMargin: 12
                            anchors.verticalCenter: parent.verticalCenter
                            readonly property var look: ({
                                "ok": ["Up to date", "#5bd18b"],
                                "update": ["Update available", "#f5a524"],
                                "missing": ["Missing", "#f2706b"],
                                "unknown": ["Installed", "#a3a8b8"]
                            })[modelData.state] || ["", "#a3a8b8"]
                            width: chip.implicitWidth + 18
                            height: 22
                            radius: 11
                            color: Qt.rgba(0, 0, 0, 0)
                            border.width: 1
                            border.color: look[1]
                            Text {
                                id: chip
                                anchors.centerIn: parent
                                text: parent.look[0]
                                color: parent.look[1]
                                font.family: app.fontFamily
                                font.pixelSize: 11
                                font.weight: Font.DemiBold
                            }
                        }
                    }
                }
                Text {
                    text: backend.updateStatus
                    color: app.faint
                    font.family: app.fontFamily
                    font.pixelSize: 12
                }
            }
        }

        GlassPanel {
            width: parent.width
            height: foldersCol.implicitHeight + 34
            Column {
                id: foldersCol
                x: 18
                y: 16
                width: parent.width - 36
                spacing: 12
                SectionTitle { text: "Your files" }
                Text {
                    width: parent.width
                    text: "Capes, texture packs, music and screenshots live in the game folder. Drop files in and Hollow picks them up."
                    color: app.dim
                    font.family: app.fontFamily
                    font.pixelSize: 13
                    wrapMode: Text.WordWrap
                }
                Flow {
                    width: parent.width
                    spacing: 8
                    HButton { small: true; icon: "folder"; text: "Mods"; onClicked: backend.openFolder("mods") }
                    HButton { small: true; icon: "image"; text: "Texture packs"; onClicked: backend.openFolder("packs") }
                    HButton { small: true; icon: "spark"; text: "Capes"; onClicked: backend.openFolder("capes") }
                    HButton { small: true; icon: "music"; text: "Music"; onClicked: backend.openFolder("music") }
                    HButton { small: true; icon: "image"; text: "Screenshots"; onClicked: backend.openFolder("screenshots") }
                }
            }
        }
    }
}
