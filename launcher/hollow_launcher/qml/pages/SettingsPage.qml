import QtQuick
import "../components"

// Settings: memory, Java options, window size, the launcher's own look, the shared theme, and sign-in setup.
Item {
    id: page
    property real appear: 1

    Flickable {
        id: flick
        anchors.fill: parent
        contentHeight: col.implicitHeight + 10
        clip: true
        boundsBehavior: Flickable.StopAtBounds
        opacity: page.appear
        transform: Translate { y: 16 * (1 - page.appear) }

        Column {
            id: col
            width: flick.width - 6
            spacing: 16

            GlassPanel {
                width: parent.width
                height: game.implicitHeight + 36
                Column {
                    id: game
                    x: 20
                    y: 18
                    width: parent.width - 40
                    spacing: 16
                    SectionTitle { text: "Game" }
                    Item {
                        width: parent.width
                        height: 48
                        Text {
                            text: "Memory"
                            color: app.text
                            font.family: app.fontFamily
                            font.pixelSize: 14
                        }
                        Text {
                            anchors.right: parent.right
                            text: (ram.shown / 1024).toFixed(1) + " GB  of " + (backend.totalRam / 1024).toFixed(0) + " GB"
                            color: app.accent
                            font.family: app.fontFamily
                            font.pixelSize: 14
                            font.weight: Font.DemiBold
                        }
                        HSlider {
                            id: ram
                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.bottom: parent.bottom
                            from: 1024
                            to: backend.ramMax
                            stepSize: 512
                            value: backend.ramMb
                            onCommitted: (v) => backend.setRam(v)
                        }
                    }
                    Column {
                        width: parent.width
                        spacing: 6
                        Text {
                            text: "Extra Java options"
                            color: app.text
                            font.family: app.fontFamily
                            font.pixelSize: 14
                        }
                        HTextField {
                            width: parent.width
                            text: backend.jvmArgs
                            placeholder: "for example -XX:+UseZGC (leave empty if unsure)"
                            monospace: true
                            onEdited: (t) => backend.setJvmArgs(t)
                        }
                    }
                    HToggle {
                        width: parent.width
                        text: "Use Minecraft's normal window size"
                        checked: backend.resWidth === 0
                        onToggled: (v) => backend.setResolution(v ? 0 : 1280, v ? 0 : 720)
                    }
                    Row {
                        visible: backend.resWidth !== 0
                        spacing: 10
                        HTextField {
                            width: 90
                            text: String(backend.resWidth)
                            validator: IntValidator { bottom: 320; top: 7680 }
                            onEdited: (t) => backend.setResolution(parseInt(t) || 1280, backend.resHeight)
                        }
                        Text {
                            text: "×"
                            color: app.dim
                            font.pixelSize: 16
                            anchors.verticalCenter: parent.verticalCenter
                        }
                        HTextField {
                            width: 90
                            text: String(backend.resHeight)
                            validator: IntValidator { bottom: 240; top: 4320 }
                            onEdited: (t) => backend.setResolution(backend.resWidth, parseInt(t) || 720)
                        }
                    }
                    HToggle {
                        width: parent.width
                        text: "Close the launcher when the game starts"
                        hint: "The game's output then isn't shown in Logs."
                        checked: backend.closeOnLaunch
                        onToggled: (v) => backend.setCloseOnLaunch(v)
                    }
                }
            }

            GlassPanel {
                width: parent.width
                height: look.implicitHeight + 36
                Column {
                    id: look
                    x: 20
                    y: 18
                    width: parent.width - 40
                    spacing: 16
                    SectionTitle { text: "Theme" }
                    Text {
                        width: parent.width
                        text: "Shared with the game: pick one here or in game (Right Shift → Client → Theme) and both change."
                        color: app.dim
                        font.family: app.fontFamily
                        font.pixelSize: 13
                        wrapMode: Text.WordWrap
                    }
                    Flow {
                        width: parent.width
                        spacing: 10
                        Repeater {
                            model: theme.names
                            Rectangle {
                                width: 116
                                height: 46
                                radius: app.radius + 2
                                readonly property bool current: modelData === theme.name
                                color: Qt.rgba(1, 1, 1, swHover.hovered ? 0.08 : 0.04)
                                border.width: current ? 2 : 1
                                border.color: current ? app.accent : Qt.rgba(1, 1, 1, 0.1)
                                Rectangle {
                                    x: 10
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: 22
                                    height: 22
                                    radius: 11
                                    gradient: Gradient {
                                        GradientStop { position: 0; color: app.presetColor(modelData, 0) }
                                        GradientStop { position: 1; color: app.presetColor(modelData, 1) }
                                    }
                                }
                                Text {
                                    x: 40
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: parent.width - 46
                                    text: modelData
                                    elide: Text.ElideRight
                                    color: app.text
                                    font.family: app.fontFamily
                                    font.pixelSize: 13
                                    font.weight: parent.current ? Font.DemiBold : Font.Normal
                                }
                                HoverHandler { id: swHover; cursorShape: Qt.PointingHandCursor }
                                TapHandler {
                                    onTapped: {
                                        sounds.play("click")
                                        theme.applyPreset(modelData)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            GlassPanel {
                width: parent.width
                height: launcherOpts.implicitHeight + 36
                Column {
                    id: launcherOpts
                    x: 20
                    y: 18
                    width: parent.width - 40
                    spacing: 16
                    SectionTitle { text: "Launcher" }
                    HToggle {
                        width: parent.width
                        text: "Animated background"
                        hint: "The moving nebula. Off saves a little power on laptops."
                        checked: backend.animatedBackground
                        onToggled: (v) => backend.setLauncherOption("animated_background", v)
                    }
                    HToggle {
                        width: parent.width
                        text: "Sounds"
                        checked: backend.uiSounds
                        onToggled: (v) => backend.setLauncherOption("ui_sounds", v)
                    }
                    HToggle {
                        width: parent.width
                        text: "Intro when the launcher opens"
                        checked: backend.showIntro
                        onToggled: (v) => backend.setLauncherOption("show_intro", v)
                    }
                }
            }

            GlassPanel {
                width: parent.width
                height: signin.implicitHeight + 36
                Column {
                    id: signin
                    x: 20
                    y: 18
                    width: parent.width - 40
                    spacing: 12
                    SectionTitle { text: "Microsoft sign-in" }
                    Text {
                        width: parent.width
                        text: backend.configStatus
                        color: backend.canLogin ? app.dim : "#ffb3b0"
                        font.family: app.fontFamily
                        font.pixelSize: 13
                        wrapMode: Text.WordWrap
                    }
                    Row {
                        spacing: 8
                        HButton { small: true; icon: "folder"; text: "Open config.json folder"; onClicked: backend.openFolder("config") }
                        HButton { small: true; text: "How to set it up"; onClicked: backend.showConfigHelp() }
                        HButton { small: true; icon: "folder"; text: "Game folder"; onClicked: backend.openFolder("game") }
                    }
                }
            }
        }
    }
}
