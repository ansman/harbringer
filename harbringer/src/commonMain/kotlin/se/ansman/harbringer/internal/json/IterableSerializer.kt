package se.ansman.harbringer.internal.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeCollection
import kotlinx.serialization.json.JsonEncoder

internal class IterableSerializer<T>(
    private val itemSerializer: KSerializer<T>,
) : KSerializer<Iterable<T>> {
    private val delegate = ListSerializer(itemSerializer)
    override val descriptor: SerialDescriptor get() = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Iterable<T>) {
        require(encoder is JsonEncoder)
        // The JSON encoder doesn't care about the collection size so we can pass 0 here
        encoder.encodeCollection(descriptor, 0) {
            value.forEachIndexed { index, item ->
                encodeSerializableElement(
                    descriptor = descriptor,
                    index = index,
                    serializer = itemSerializer,
                    value = item
                )
            }
        }
    }

    override fun deserialize(decoder: Decoder): Iterable<T> = delegate.deserialize(decoder)
}