package se.ansman.harbringer.internal.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable(with = LazyString.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
internal class LazyString(private val stringProducer: () -> String?) {
    fun produceString(): String? = stringProducer()

    object Serializer : KSerializer<LazyString> {
        override val descriptor get() = String.serializer().nullable.descriptor

        override fun deserialize(decoder: Decoder): LazyString {
            val string = if (decoder.decodeNotNullMark()) {
                decoder.decodeString()
            } else {
                decoder.decodeNull()
            }
            return LazyString { string }
        }

        override fun serialize(encoder: Encoder, value: LazyString) {
            val string = value.produceString()
            if (string == null) {
                encoder.encodeNull()
            } else {
                encoder.encodeString(string)
            }
        }
    }
}