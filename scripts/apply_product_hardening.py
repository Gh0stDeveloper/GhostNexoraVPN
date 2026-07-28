from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"Expected block not found in {path}: {old[:120]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/src/main/java/com/ghostnexora/vpn/ui/screens/profiles/CreateEditProfileScreen.kt",
    '''                    if (state.selectedMode.requiresPayload) {
                        PayloadPresetPanel(
                            host = state.host,
                            port = state.port.toIntOrNull() ?: 443,
                            sni = state.sni.ifBlank { state.host },
                            onUsePayload = viewModel::onPayloadChange
                        )
                        OutlinedTextField(
                            value = state.payload,
                            onValueChange = viewModel::onPayloadChange,
                            label = { Text("Payload HTTP") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            supportingText = {
                                Text("Variables: [host], [host_port], [port], [sni], [crlf].")
                            }
                        )
                    }
''',
    '''                    if (state.selectedMode.requiresPayload) {
                        AdvancedPayloadEditor(
                            payload = state.payload,
                            host = state.host,
                            port = state.port.toIntOrNull() ?: 443,
                            sni = state.sni,
                            proxyHost = state.proxyHost,
                            proxyPort = state.proxyPort.toIntOrNull() ?: 0,
                            onPayloadChange = viewModel::onPayloadChange
                        )
                    }
''',
)

print("Advanced payload editor wired")
