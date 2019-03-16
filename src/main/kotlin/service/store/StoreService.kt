package service.store

import io.ktor.features.BadRequestException
import io.ktor.features.NotFoundException
import model.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import service.store.data.ThinPokemon
import service.user.balance.BalanceIncreaseRateManager
import service.user.balance.BalanceIncreaseRateManager.Companion.IncreaseRateScalingFactor
import service.user.balance.BalanceManager
import utility.PokeApi
import kotlin.random.Random

const val BoosterpackSize = 5
const val LocationIdBaseIncrease = 3.0
const val SecondsInFiveMinutes = 300
const val BoosterpackAmountLimit = 25

class StoreService {
    fun getAllBoosterpacks(): List<Boosterpack> {
        return PokeApi().client.getLocationList(0, BoosterpackAmountLimit).results.mapNotNull { getSpecificBoosterpack(it.id) }
    }

    fun getSpecificBoosterpack(id: Int): Boosterpack? {
        val location = PokeApi().getLocation(id)
        val pokemonsInLocation = PokeApi().getPokemonsOfLocation(location)
        if (pokemonsInLocation.isEmpty()) return null

        val price = determineBoosterpackPrice(location.id)

        return Boosterpack(
            name = capitalizeLocationName(location.name),
            price = price,
            locationId = location.id,
            hexColor = determineHexColorBasedOnLocationId(location.id),
            pokemons = pokemonsInLocation
        )
    }

    fun buyBoosterpack(id: Int, user: User): List<model.Pokemon> {
        // Retrieve the information necessary to open a new boosterpack
        val boosterpack = getSpecificBoosterpack(id) ?: throw NotFoundException("This boosterpack does not exist")

        // Retrieve current balance of user
        val balance = BalanceManager(user).retrieveCurrentBalance()

        // Check if user has enough money
        if (balance < boosterpack.price) throw BadRequestException("User does not own enough PokéDollars")

        // Open the booster pack
        val receivedPokemons = openBoosterpack(boosterpack.pokemons, boosterpack.price)

        // Insert the received Pokemon into the database
        val insertedPokemons = insertReceivedPokemon(receivedPokemons, user)

        // Subtract the booster pack price from the user's account balance
        Users.subtractPokeDollarsFromBalance(user.id, boosterpack.price)

        // Update the gather rate of the user
        BalanceIncreaseRateManager(user).updateIncreaseRate()

        return insertedPokemons
    }

    private fun openBoosterpack(pokemons: List<ThinPokemon>, boosterpackPrice: Long): List<ThinPokemon> {
        val sortedPokemons = pokemons.sortedBy { it.xp }.asReversed()

        val possiblePokemons = arrayListOf<ThinPokemon>()
        sortedPokemons.forEachIndexed { index, pokemon -> repeat(index + 1) { possiblePokemons.add(pokemon) } }

        val drawnPokemons = possiblePokemons.shuffled().take(BoosterpackSize)
        drawnPokemons.forEach { it.xp = Math.ceil((getReasonablyNormallyDistributedDouble() + 1) * boosterpackPrice / (SecondsInFiveMinutes * BoosterpackSize)).toInt() }

        return drawnPokemons
    }

    private fun insertReceivedPokemon(drawnPokemons: List<ThinPokemon>, user: User): List<model.Pokemon> {
        val receivedPokemons = arrayListOf<model.Pokemon>()

        transaction {
            for (pokemon in drawnPokemons) {
                val insertedPokemon = Pokemons.insert {
                    it[pokeNumber] = pokemon.id
                    it[owner] = user.id
                    it[xp] = pokemon.xp
                    it[aquisitionDateTime] = DateTime()
                }

                receivedPokemons.add(
                    model.Pokemon(
                        id = insertedPokemon.resultedValues!!.first()[Pokemons.id],
                        pokeNumber = pokemon.id,
                        xp = pokemon.xp,
                        aquisitionDateTime = DateTime(),
                        thinApiInfo = pokemon
                    )
                )
            }
        }

        return receivedPokemons
    }

    private fun capitalizeLocationName(locationName: String): String {
        return locationName.split("-").joinToString(" ") { it.capitalize() }
    }

    private fun determineBoosterpackPrice(locationId: Int): Long {
        return (BoosterpackSize * SecondsInFiveMinutes * Math.pow(LocationIdBaseIncrease, (locationId - 1).toDouble()) / IncreaseRateScalingFactor).toLong()
    }

    private fun determineHexColorBasedOnLocationId(locationId: Int): String {
        var s0 = (Math.sqrt(5.0) * locationId * 0x1000000).toLong()
        val s1 = locationId.toLong()

        s0 = s0 xor (s0 shl 23)
        s0 = s0 xor s0.ushr(17)
        s0 = s0 xor (s1 xor s1.ushr(26))
        val col = (s0 + s1) and 0xffffff

        return col.toString(16).padStart(6, '0')
    }

    private fun getReasonablyNormallyDistributedDouble(): Double {
        return ((Random.nextDouble() + Random.nextDouble() + Random.nextDouble()) / 3)
    }
}
